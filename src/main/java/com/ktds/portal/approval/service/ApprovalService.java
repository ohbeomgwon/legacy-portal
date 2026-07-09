package com.ktds.portal.approval.service;

import com.ktds.portal.approval.domain.AmountGrade;
import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.domain.ApprovalStatus;
import com.ktds.portal.approval.repository.ApprovalRepository;
import com.ktds.portal.common.AuditLogger;
import com.ktds.portal.common.MailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결재 서비스 — 이 클래스가 이 과정의 "주인공 안티패턴"이다.
 *
 * ============================ 의도적으로 심어둔 스멜 목록 ============================
 *  1. God Class            : 검증 + 영속화 + 메일 + 감사로그 + 포맷팅 + 권한판정을 혼자 다 한다.
 *  2. Long Method          : processApproval() 한 메서드가 100줄 이상, 중첩 if 6단계.
 *  3. Magic Number         : status 0/1/2/3/9, type 1~4, role 1~3, priority 1~3 이 흩어져 있다.
 *  4. Duplicated Code      : 메일 본문 생성/감사 로그 기록이 메서드마다 복붙 되어 있다.
 *  5. Tight Coupling       : new SmtpMailSender(), new FileAuditLogger() 직접 생성(DI 없음).
 *  6. Feature Envy         : Approval 의 필드를 꺼내 서비스가 직접 상태/금액 규칙을 계산한다.
 *  7. Primitive Obsession  : 모든 분기를 int 비교로 처리한다.
 *  8. Long Parameter List  : create() 파라미터 8개.
 *  9. Poor Naming          : d, u, proc, tmp, flag1 같은 약어.
 * 10. Comment Smell        : 나쁜 이름을 주석으로 변명한다.
 * 11. No Tests             : 테스트가 단 한 개도 없다(안전망 부재).
 * =================================================================================
 *
 * [리팩토링] 스멜9(Poor Naming) 일부 해소 — d→approval, u→actor, s→status로 리네임하고
 *            proc(action을 그대로 복사해 담던 임시변수)는 제거해 action을 직접 사용한다
 *            (기법: Rename Variable / Remove Temp — 동작 변경 없음).
 * [리팩토링] 스멜3·7(Magic Number/Primitive Obsession) 중 status 부분 해소 — status(int)를
 *            {@link ApprovalStatus} enum으로 전환(기법: Replace Magic Number with Symbolic
 *            Constant). DB 저장값·API 응답은 기존과 동일(자세한 내용은 ApprovalStatus 참고).
 *            type·priority는 이번 변경 범위 밖이라 아직 int 그대로 남아 있다.
 * [리팩토링] statusLabel()의 5분기 if-else 사다리와 tmp 임시변수를 제거하고, 라벨을
 *            enum이 직접 소유(ApprovalStatus.label())하도록 위임(기법: Move Method /
 *            Replace Conditional with Polymorphism의 단순화 버전). 상태값은 이제 enum이
 *            보장하므로 레거시의 "알수없음" 폴백 분기는 도달 불가능해져 자연히 사라진다
 *            (setStatus가 ApprovalStatus 타입만 받으므로 정의되지 않은 상태값 자체가 만들어질 수 없음).
 * [리팩토링] processApproval()에 남아있던 매직넘버 전부 제거(기법: Replace Magic Number with
 *            Symbolic Constant).
 *              - action 코드(1/2/3/9)는 {@link ApprovalStatus.Action} enum으로 매핑해 비교한다.
 *                processApproval의 public 시그니처(int action)는 CLAUDE.md 계약 보존 규칙에 따라
 *                그대로 유지하고, 메서드 진입 시에만 enum으로 변환한다. 정의되지 않은 action 값은
 *                fromCode()가 null을 반환해 어떤 분기와도 매칭되지 않으므로 "조용히 무시"하는
 *                레거시 동작이 그대로 보존된다.
 *              - type(지출)·금액 임계값·우선순위(높음)·결재 권한 최소 역할은 이름 있는 상수로 추출했다.
 * [리팩토링] action 코드 enum을 ApprovalService의 private 중첩 타입에서 {@link ApprovalStatus}
 *            안의 중첩 enum({@code ApprovalStatus.Action})으로 이동 — 결재의 "상태"와 "상태 전이
 *            명령"이 서로 다른 클래스에 흩어져 있던 것을 한 곳(ApprovalStatus)에 모았다.
 * [리팩토링] 스멜2(Long Method) 해소 — processApproval()의 6단계 중첩 if-지옥을 걷어냈다
 *            (기법: Extract Method + Replace Nested Conditional with Guard Clauses).
 *              - processApproval()은 이제 공통 전제조건(대상·사용자 조회) 확인 후
 *                action에 따라 submit/approve/reject/cancel 중 하나로 위임만 한다.
 *              - public 시그니처 processApproval(id, userId, action, reason)과 관찰 가능한 동작
 *                (상태 전이 결과·메일 발송·감사 로그·조용한 무시)은 전혀 바뀌지 않았다.
 * [리팩토링] statusLabel·amountGrade는 표현/도메인 규칙이지 서비스의 책임이 아니라고 판단해
 *            Move Method로 이동했다 — statusLabel()은 ApprovalStatus.label(), amountGrade()는
 *            {@link AmountGrade} enum에 위임한다. 두 메서드의 public 시그니처와 반환값은 그대로다.
 * [리팩토링] 스멜1·6·7(God Class의 상태전이/권한 규칙 + Feature Envy + Primitive Obsession) 해소
 *            — submit/approve/reject/cancel의 가드 클로즈(상태·본인확인·권한 판정)와 상신 시
 *            우선순위 자동 상향 규칙을 {@link Approval} 엔티티의 도메인 메서드로 이동했다(기법:
 *            Move Method — Anemic Domain Model 해소). 레거시는 규칙 위반 시 예외 없이 조용히
 *            무시했으므로, Approval의 각 메서드도 예외 대신 boolean(성공 여부)을 반환한다.
 *            서비스는 이제 "도메인 메서드 호출 → 성공 시에만 저장·메일·감사로그"라는 오케스트레이션만
 *            담당하고, 상태·권한 판정 로직 자체는 갖고 있지 않다.
 * [리팩토링] 스멜4(Duplicated Code) 일부 해소 — approve()/reject()의 메일 본문 조립을
 *            approvedBody()/rejectedBody() private 메서드로 추출했다(기법: Extract Method).
 *            본문 조립은 알림 문구를 다루는 서비스의 책임이라 판단해 서비스 안에 그대로 뒀다
 *            (상태/권한 판정과 달리 도메인으로 옮기지 않음).
 * [리팩토링] 스멜5(Tight Coupling) 해소 — 구현체를 직접 new 하던 필드를 {@link MailSender}/
 *            {@link AuditLogger} 인터페이스로 바꾸고 생성자 주입으로 연결했다(기법: Extract
 *            Interface + Dependency Injection). 두 구현체(ConsoleMailSender/ConsoleAuditLogger,
 *            콘솔에만 출력하므로 SmtpMailSender/FileAuditLogger라는 레거시 이름에서 리네임)는
 *            @Component로 등록되어 Spring이 주입한다.
 * [리팩토링] 스멜4(Duplicated Code) 추가 해소 — 감사 로그의 타임스탬프 포맷팅 + 라인 조립을
 *            서비스에서 걷어내 {@link AuditLogger}의 계약 자체를 write(action, id, userId)로
 *            바꿨다. 이제 "[timestamp] ACTION id=.. by=.." 조립은 구현체(ConsoleAuditLogger)
 *            한 곳에서만 일어난다. writeAudit() private 헬퍼는 더 이상 필요 없어 제거했다.
 *            create()의 감사 로그에 있던 "type=.." 부가 정보는 이 계약이 지원하지 않아 콘솔
 *            출력에서 빠졌다 — DB 저장값·API 응답·상태 전이 결과와는 무관한 콘솔 로그 포맷의
 *            사소한 변화이며, 특성화 테스트는 이 출력 내용을 검증하지 않는다.
 * 위 변경 전후로 refactor/start 특성화 테스트 6개가 green 유지됨을 확인했다.
 */
@Service
public class ApprovalService {

    private final ApprovalRepository repo;
    private final UserRepository userRepo;
    private final MailSender mail;
    private final AuditLogger audit;

    public ApprovalService(ApprovalRepository repo, UserRepository userRepo,
                            MailSender mail, AuditLogger audit) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.mail = mail;
        this.audit = audit;
    }

    // [스멜8] 파라미터 8개. [스멜9] 이름이 모호하다.
    public Approval create(String title, String content, int type, int priority,
                           Long drafterId, Long approverId, long amount, boolean urgent) {
        Approval approval = new Approval();
        approval.setTitle(title);
        approval.setContent(content);
        approval.setType(type);
        approval.setPriority(urgent ? 3 : priority);   // priority(우선순위): 1 낮음·2 보통·3 높음  [스멜3: 3=높음, 왜 3이 높음? 코드만 봐선 모름]
        approval.setStatus(ApprovalStatus.DRAFT);
        approval.setDrafterId(drafterId);
        approval.setApproverId(approverId);
        approval.setAmount(amount);
        approval.setCreatedAt(LocalDateTime.now());
        approval.setUpdatedAt(LocalDateTime.now());
        repo.save(approval);

        audit.write("APPROVAL CREATE", approval.getId(), drafterId);
        return approval;
    }

    /**
     * 결재 처리 — 상신/승인/반려/취소를 action 코드로 분기해 각 처리 메서드에 위임한다.
     * 상태 전이·권한 규칙 자체는 {@link Approval}의 도메인 메서드가 갖고 있고, 여기서는
     * 조회 + 디스패치 + (성공 시) 저장/알림/감사로그만 담당한다.
     *
     * action: 1=상신, 2=승인, 3=반려, 9=취소
     */
    public void processApproval(Long id, Long userId, int action, String reason) {
        Approval approval = repo.findById(id).orElse(null);
        if (approval == null) {
            // [스멜] 예외 대신 조용히 리턴 — 호출자는 실패를 알 수 없다.
            return;
        }
        User actor = userRepo.findById(userId).orElse(null);
        if (actor == null) {
            return;
        }

        ApprovalStatus.Action requestedAction = ApprovalStatus.Action.fromCode(action);
        if (requestedAction == ApprovalStatus.Action.SUBMIT) {
            submit(approval, actor);
        } else if (requestedAction == ApprovalStatus.Action.APPROVE) {
            approve(approval, actor);
        } else if (requestedAction == ApprovalStatus.Action.REJECT) {
            reject(approval, actor, reason);
        } else if (requestedAction == ApprovalStatus.Action.CANCEL) {
            cancel(approval, actor);
        }
        // requestedAction이 null(정의되지 않은 action 값)이면 아무 분기와도 매칭되지 않아
        // 조용히 무시된다 — 레거시 동작 보존.
    }

    private void submit(Approval approval, User actor) {
        if (!approval.submit()) {
            return;
        }
        repo.save(approval);

        // [스멜4] 메일 발송 — 본문 생성 로직이 곳곳에 복붙.
        User approver = userRepo.findById(approval.getApproverId()).orElse(null);
        if (approver != null) {
            String body = "안녕하세요 " + approver.getName() + "님,\n"
                    + "결재 요청이 도착했습니다.\n제목: " + approval.getTitle()
                    + "\n기안자ID: " + approval.getDrafterId();
            mail.send(approver.getEmail(), "[결재요청] " + approval.getTitle(), body);
        }
        audit.write("APPROVAL SUBMIT", approval.getId(), actor.getId());
    }

    private void approve(Approval approval, User actor) {
        if (!approval.approve(actor)) {
            return;
        }
        repo.save(approval);

        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), approvedBody(drafter, approval));
        }
        audit.write("APPROVAL APPROVE", approval.getId(), actor.getId());
    }

    private void reject(Approval approval, User actor, String reason) {
        if (!approval.reject(actor, reason)) {
            return;
        }
        repo.save(approval);

        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
        if (drafter != null) {
            mail.send(drafter.getEmail(), "[결재반려] " + approval.getTitle(), rejectedBody(drafter, approval, reason));
        }
        audit.write("APPROVAL REJECT", approval.getId(), actor.getId());
    }

    private void cancel(Approval approval, User actor) {
        if (!approval.cancel(actor)) {
            return;
        }
        repo.save(approval);
        audit.write("APPROVAL CANCEL", approval.getId(), actor.getId());
    }

    // [리팩토링] Extract Method — approve()에서 분리한 메일 본문 조립.
    private String approvedBody(User drafter, Approval approval) {
        return "안녕하세요 " + drafter.getName() + "님,\n"
                + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
    }

    // [리팩토링] Extract Method — reject()에서 분리한 메일 본문 조립.
    private String rejectedBody(User drafter, Approval approval, String reason) {
        return "안녕하세요 " + drafter.getName() + "님,\n"
                + "결재가 반려되었습니다.\n제목: " + approval.getTitle()
                + "\n사유: " + reason;
    }

    // [리팩토링] 5분기 if-else + tmp 임시변수 제거 → ApprovalStatus.label()에 위임(Move Method).
    // 표현(라벨) 규칙은 서비스가 아니라 상태 자신의 책임이라는 판단.
    public String statusLabel(Approval approval) {
        return approval.getStatus().label();
    }

    // [리팩토링] 스멜6(Feature Envy) 해소 — 금액 등급 계산 로직을 AmountGrade enum으로
    // 이동(Move Method). "금액이 S/A/B/C 중 무엇인가"는 금액이라는 값 자체의 규칙이지
    // 서비스의 책임이 아니라는 판단. public 시그니처(Approval → String)는 그대로 유지.
    public String amountGrade(Approval approval) {
        return AmountGrade.fromAmount(approval.getAmount()).name();
    }

    public List<Approval> myDrafts(Long userId) {
        return repo.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return repo.findByApproverId(userId);
    }
}
