package com.ktds.portal.approval;

import com.ktds.portal.common.FileAuditLogger;
import com.ktds.portal.common.SmtpMailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * 위 변경 전후로 refactor/start 특성화 테스트 6개가 green 유지됨을 확인했다.
 */
@Service
public class ApprovalService {

    private final ApprovalRepository repo;
    private final UserRepository userRepo;

    // [스멜5] 강결합 — 협력 객체를 생성자 주입 없이 직접 new 한다. 테스트에서 갈아끼울 수 없다.
    private final SmtpMailSender mail = new SmtpMailSender();
    private final FileAuditLogger audit = new FileAuditLogger();

    public ApprovalService(ApprovalRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
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

        // [스멜4] 감사 로그 기록 — 이 6줄이 submit/approve/reject/cancel 에도 복붙 되어 있다.
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = "[" + now + "] APPROVAL CREATE id=" + approval.getId()
                + " by=" + drafterId + " type=" + approval.getType();
        audit.write(line);
        return approval;
    }

    /**
     * 결재 처리 — 상신/승인/반려/취소를 action 코드로 분기한다.
     * [스멜2] 이 메서드 하나가 모든 일을 한다. [스멜1][스멜6] 규칙 계산을 서비스가 떠안는다.
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

        ApprovalStatus status = approval.getStatus();

        // [스멜2][스멜3] 거대한 if-지옥. 상태 전이 규칙이 숫자 비교로 흩어져 있다.
        if (action == 1) {            // action==1 → 상신
            // 상신: 임시저장일 때만 가능
            if (status == ApprovalStatus.DRAFT) {
                // [스멜6] 금액 기준 결재자 자동 상향 — 도메인 규칙이 서비스에 박혀 있다.
                if (approval.getType() == 1 && approval.getAmount() >= 1000000) {   // type 1=지출·2=휴가·3=구매·4=기타 → type==1(지출) && 100만원↑
                    approval.setPriority(3);   // 3 = 높음
                }
                approval.setStatus(ApprovalStatus.SUBMITTED);
                approval.setUpdatedAt(LocalDateTime.now());
                repo.save(approval);
                // [스멜4] 메일 발송 — 본문 생성 로직이 곳곳에 복붙.
                User approver = userRepo.findById(approval.getApproverId()).orElse(null);
                if (approver != null) {
                    String body = "안녕하세요 " + approver.getName() + "님,\n"
                            + "결재 요청이 도착했습니다.\n제목: " + approval.getTitle()
                            + "\n기안자ID: " + approval.getDrafterId();
                    mail.send(approver.getEmail(), "[결재요청] " + approval.getTitle(), body);
                }
                writeAudit("APPROVAL SUBMIT", approval.getId(), userId);
            }
        } else if (action == 2) {     // action==2 → 승인
            // 승인: 상신 상태 + 본인이 결재자 + 권한(role>=2) 일 때만
            if (status == ApprovalStatus.SUBMITTED) {
                if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
                    if (actor.getRole() >= 2) {   // role 1=사원·2=팀장·3=임원 (role>=2 승인권한)  [스멜3: 숫자로 권한 판정]
                        approval.setStatus(ApprovalStatus.APPROVED);
                        approval.setUpdatedAt(LocalDateTime.now());
                        repo.save(approval);
                        // [스멜4] 또 복붙된 메일 발송
                        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
                        if (drafter != null) {
                            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                                    + "결재가 승인되었습니다.\n제목: " + approval.getTitle();
                            mail.send(drafter.getEmail(), "[결재승인] " + approval.getTitle(), body);
                        }
                        writeAudit("APPROVAL APPROVE", approval.getId(), userId);
                    }
                }
            }
        } else if (action == 3) {     // action==3 → 반려
            // 반려
            if (status == ApprovalStatus.SUBMITTED) {
                if (approval.getApproverId() != null && approval.getApproverId().equals(userId)) {
                    if (actor.getRole() >= 2) {   // role>=2 → 팀장 이상 (위 승인 분기와 똑같은 판정 복붙)
                        approval.setStatus(ApprovalStatus.REJECTED);
                        approval.setRejectReason(reason);
                        approval.setUpdatedAt(LocalDateTime.now());
                        repo.save(approval);
                        User drafter = userRepo.findById(approval.getDrafterId()).orElse(null);
                        if (drafter != null) {
                            String body = "안녕하세요 " + drafter.getName() + "님,\n"
                                    + "결재가 반려되었습니다.\n제목: " + approval.getTitle()
                                    + "\n사유: " + reason;
                            mail.send(drafter.getEmail(), "[결재반려] " + approval.getTitle(), body);
                        }
                        writeAudit("APPROVAL REJECT", approval.getId(), userId);
                    }
                }
            }
        } else if (action == 9) {     // action==9 → 취소 (왜 9? 4~8 은 비워둔 규칙 없는 번호)
            // 취소: 기안자 본인 + 아직 승인 전(임시저장 또는 상신)
            if (status == ApprovalStatus.DRAFT || status == ApprovalStatus.SUBMITTED) {
                if (approval.getDrafterId() != null && approval.getDrafterId().equals(userId)) {
                    approval.setStatus(ApprovalStatus.CANCELED);
                    approval.setUpdatedAt(LocalDateTime.now());
                    repo.save(approval);
                    writeAudit("APPROVAL CANCEL", approval.getId(), userId);
                }
            }
        }
    }

    // [스멜4] 그나마 추출했지만 create() 안에는 또 복붙이 남아 있다(불완전한 중복 제거).
    private void writeAudit(String act, Long id, Long userId) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        audit.write("[" + now + "] " + act + " id=" + id + " by=" + userId);
    }

    // [리팩토링] 5분기 if-else + tmp 임시변수 제거 → ApprovalStatus.label()에 위임.
    public String statusLabel(Approval approval) {
        return approval.getStatus().label();
    }

    // [스멜6] Feature Envy — Approval 데이터를 꺼내 금액 등급을 서비스가 계산.
    public String amountGrade(Approval approval) {
        long amount = approval.getAmount();
        if (amount >= 10000000) return "S";   // [스멜3] 1000만원=S — 기준 숫자의 의미가 코드에 없음
        else if (amount >= 1000000) return "A";   // 100만원=A
        else if (amount >= 100000) return "B";    // 10만원=B
        else return "C";
    }

    public List<Approval> myDrafts(Long userId) {
        return repo.findByDrafterId(userId);
    }

    public List<Approval> myInbox(Long userId) {
        return repo.findByApproverId(userId);
    }
}
