package com.ktds.portal.approval;

import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.repository.ApprovalRepository;
import com.ktds.portal.approval.service.ApprovalService;
import com.ktds.portal.common.ConsoleAuditLogger;
import com.ktds.portal.common.MailSender;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [새 테스트] processApproval이 실제로 {@link MailSender#send}를 호출하는지 검증하는 단위 테스트.
 * 기존 안전망인 ApprovalServiceProcessApprovalCharacterizationTest(refactor/start)는 건드리지
 * 않고 별도 파일로 추가했다.
 *
 * Spring 빈으로 등록하지 않고, FakeMailSender를 테스트 코드에서 직접 {@code new}로 만들어
 * ApprovalService 생성자에 바로 넘긴다 — 생성자 주입 자체가 "구현체를 쉽게 갈아끼울 수 있다"는
 * DI의 이점을 보여준다(과거 강결합 시절엔 이 방식의 테스트가 아예 불가능했다, docs/4-9 참고).
 * repo/userRepo는 @DataJpaTest가 제공하는 실제 JPA 리포지토리를 그대로 쓰고, ApprovalService만
 * 이 테스트에서 직접 조립한다.
 */
@DataJpaTest
class ApprovalServiceMailNotificationTest {

    static class FakeMailSender implements MailSender {
        record SentMail(String to, String subject, String body) {
        }

        final List<SentMail> sent = new ArrayList<>();

        @Override
        public void send(String to, String subject, String body) {
            sent.add(new SentMail(to, subject, body));
        }
    }

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    private FakeMailSender fakeMailSender;
    private ApprovalService approvalService;

    private User 기안자;
    private User 결재자;

    @BeforeEach
    void setUp() {
        // [핵심] FakeMailSender를 직접 new로 생성해 ApprovalService 생성자에 그대로 전달한다.
        fakeMailSender = new FakeMailSender();
        approvalService = new ApprovalService(approvalRepository, userRepository,
                fakeMailSender, new ConsoleAuditLogger());

        기안자 = userRepository.save(new User("김사원", "kim@test.com", 1, "개발1팀"));
        결재자 = userRepository.save(new User("박팀장", "park@test.com", 2, "개발1팀"));
    }

    private Approval 임시저장_결재_생성(Long approverId) {
        return approvalService.create("노트북 구매", "개발용 노트북", 1, 2,
                기안자.getId(), approverId, 500_000L, false);
    }

    @Test
    @DisplayName("상신하면 결재자에게 메일이 발송된다")
    void 상신하면_결재자에게_메일이_발송된다() {
        Approval 결재 = 임시저장_결재_생성(결재자.getId());

        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");

        assertThat(fakeMailSender.sent).hasSize(1);
        FakeMailSender.SentMail mail = fakeMailSender.sent.get(0);
        assertThat(mail.to()).isEqualTo("park@test.com");
        assertThat(mail.subject()).isEqualTo("[결재요청] 노트북 구매");
    }

    @Test
    @DisplayName("승인하면 기안자에게 메일이 발송된다")
    void 승인하면_기안자에게_메일이_발송된다() {
        Approval 결재 = 임시저장_결재_생성(결재자.getId());
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");
        fakeMailSender.sent.clear(); // 상신 메일 제외하고 승인 메일만 검증

        approvalService.processApproval(결재.getId(), 결재자.getId(), 2, "");

        assertThat(fakeMailSender.sent).hasSize(1);
        FakeMailSender.SentMail mail = fakeMailSender.sent.get(0);
        assertThat(mail.to()).isEqualTo("kim@test.com");
        assertThat(mail.subject()).isEqualTo("[결재승인] 노트북 구매");
    }

    @Test
    @DisplayName("반려하면 기안자에게 메일이 발송된다")
    void 반려하면_기안자에게_메일이_발송된다() {
        Approval 결재 = 임시저장_결재_생성(결재자.getId());
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");
        fakeMailSender.sent.clear();

        approvalService.processApproval(결재.getId(), 결재자.getId(), 3, "예산 초과");

        assertThat(fakeMailSender.sent).hasSize(1);
        FakeMailSender.SentMail mail = fakeMailSender.sent.get(0);
        assertThat(mail.to()).isEqualTo("kim@test.com");
        assertThat(mail.subject()).isEqualTo("[결재반려] 노트북 구매");
    }

    @Test
    @DisplayName("취소는 메일을 보내지 않는다")
    void 취소는_메일을_보내지_않는다() {
        Approval 결재 = 임시저장_결재_생성(결재자.getId());

        approvalService.processApproval(결재.getId(), 기안자.getId(), 9, "");

        assertThat(fakeMailSender.sent).isEmpty();
    }

    @Test
    @DisplayName("권한 없는 승인 시도는 메일을 보내지 않는다")
    void 권한없는_승인시도는_메일을_보내지_않는다() {
        User 권한없는결재자 = userRepository.save(new User("최사원", "choi@test.com", 1, "개발2팀"));
        Approval 결재 = 임시저장_결재_생성(권한없는결재자.getId());
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, "");
        fakeMailSender.sent.clear();

        approvalService.processApproval(결재.getId(), 권한없는결재자.getId(), 2, "");

        assertThat(fakeMailSender.sent).isEmpty();
    }
}
