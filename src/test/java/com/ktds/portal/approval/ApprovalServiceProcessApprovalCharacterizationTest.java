package com.ktds.portal.approval;

import com.ktds.portal.approval.domain.Approval;
import com.ktds.portal.approval.domain.ApprovalStatus;
import com.ktds.portal.approval.repository.ApprovalRepository;
import com.ktds.portal.approval.service.ApprovalService;
import com.ktds.portal.user.User;
import com.ktds.portal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * [리팩토링 전 안전망] processApproval(id, userId, action, reason)의 현재 관찰 동작을 고정하는
 * 특성화 테스트(Characterization Test). 리팩토링(God Method 분해 등) 전후로 이 6개 테스트가
 * 변경 없이 green을 유지해야 "동작을 바꾸지 않았다"고 확인할 수 있다(CLAUDE.md 검증 규칙).
 *
 * 옳고 그름을 판단하지 않는다 — 권한 없는 승인이 조용히 무시되는 것도, 존재하지 않는 id를 던져도
 * 예외가 없는 것도 "현재 레거시가 그렇게 동작한다"는 사실만 고정한다.
 *
 * @DataJpaTest 는 JPA 슬라이스 테스트라 @Service 빈을 기본적으로 스캔하지 않으므로,
 * ApprovalService 를 @Import 로 컨텍스트에 직접 올리고 @Autowired 로만 주입받는다(직접 new 금지).
 */
@DataJpaTest
@Import(ApprovalService.class)
class ApprovalServiceProcessApprovalCharacterizationTest {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private UserRepository userRepository;

    private User 기안자;
    private User 결재자;
    private User 권한없는결재자;

    @BeforeEach
    void setUp() {
        // role: 1=사원 2=팀장 3=임원 (User.java 참고)
        기안자 = userRepository.save(new User("김사원", "kim@test.com", 1, "개발1팀"));
        결재자 = userRepository.save(new User("박팀장", "park@test.com", 2, "개발1팀"));
        // 결재자로 지정되었지만 권한(role>=2)이 없는 사용자 — 레거시는 생성 시점에 승인권한을 검증하지 않는다.
        권한없는결재자 = userRepository.save(new User("최사원", "choi@test.com", 1, "개발2팀"));
    }

    private Approval 임시저장_결재_생성(Long approverId) {
        // type=1(지출), amount=500_000 — 100만원 미만이라 상신 시 우선순위 자동 상향 규칙과 무관하게 둔다.
        return approvalService.create("노트북 구매", "개발용 노트북", 1, 2,
                기안자.getId(), approverId, 500_000L, false);
    }

    @Test
    @DisplayName("1) 상신 후 승인하면 상태가 SUBMITTED를 거쳐 APPROVED가 된다")
    void 상신후_승인하면_승인상태가_된다() {
        Approval 결재 = 임시저장_결재_생성(결재자.getId());
        assertThat(결재.getStatus()).isEqualTo(ApprovalStatus.DRAFT); // 생성 직후 = 임시저장

        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, ""); // 상신
        assertThat(approvalRepository.findById(결재.getId()).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.SUBMITTED);

        approvalService.processApproval(결재.getId(), 결재자.getId(), 2, ""); // 승인
        assertThat(approvalRepository.findById(결재.getId()).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("2) 상신 후 반려하면 상태가 REJECTED가 되고 반려 사유가 저장된다")
    void 상신후_반려하면_반려상태가_되고_사유가_저장된다() {
        Approval 결재 = 임시저장_결재_생성(결재자.getId());
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, ""); // 상신

        approvalService.processApproval(결재.getId(), 결재자.getId(), 3, "예산 초과"); // 반려

        Approval 결과 = approvalRepository.findById(결재.getId()).orElseThrow();
        assertThat(결과.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(결과.getRejectReason()).isEqualTo("예산 초과");
    }

    @Test
    @DisplayName("3) 기안자가 임시저장 상태에서 취소하면 상태가 CANCELED가 된다")
    void 임시저장_상태에서_취소하면_취소상태가_된다() {
        Approval 결재 = 임시저장_결재_생성(결재자.getId());

        approvalService.processApproval(결재.getId(), 기안자.getId(), 9, ""); // 취소

        assertThat(approvalRepository.findById(결재.getId()).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.CANCELED);
    }

    @Test
    @DisplayName("4) 결재자로 지정되었어도 role<2(권한 없음)면 승인 시도가 조용히 무시된다")
    void 권한없는_결재자가_승인시도하면_무시된다() {
        Approval 결재 = 임시저장_결재_생성(권한없는결재자.getId());
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, ""); // 상신 → status=1

        approvalService.processApproval(결재.getId(), 권한없는결재자.getId(), 2, ""); // 승인 시도(권한 없음)

        // 예외 없이 조용히 무시 — 상태가 그대로 SUBMITTED에 머문다.
        assertThat(approvalRepository.findById(결재.getId()).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.SUBMITTED);
    }

    @Test
    @DisplayName("5) 존재하지 않는 id로 처리를 시도하면 예외 없이 조용히 무시된다")
    void 존재하지_않는_id로_처리하면_예외없이_무시된다() {
        long 없는Id = 999_999L;
        assertThat(approvalRepository.findById(없는Id)).isEmpty();

        assertThatCode(() -> approvalService.processApproval(없는Id, 기안자.getId(), 2, ""))
                .doesNotThrowAnyException();

        assertThat(approvalRepository.findById(없는Id)).isEmpty();
    }

    @Test
    @DisplayName("6) 이미 승인된 건을 다시 승인 시도하면 상태 변화 없이 조용히 무시된다")
    void 이미_승인된_건을_재승인하면_무시된다() {
        Approval 결재 = 임시저장_결재_생성(결재자.getId());
        approvalService.processApproval(결재.getId(), 기안자.getId(), 1, ""); // 상신
        approvalService.processApproval(결재.getId(), 결재자.getId(), 2, ""); // 1차 승인
        assertThat(approvalRepository.findById(결재.getId()).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        approvalService.processApproval(결재.getId(), 결재자.getId(), 2, ""); // 재승인 시도

        assertThat(approvalRepository.findById(결재.getId()).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }
}
