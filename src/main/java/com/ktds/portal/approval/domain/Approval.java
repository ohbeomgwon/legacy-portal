package com.ktds.portal.approval.domain;

import com.ktds.portal.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 결재 엔티티.
 *
 * [스멜] 빈약한 도메인 모델(Anemic Domain Model) — 데이터만 있고 행위가 없다.
 * [스멜] 원시 타입 집착(Primitive Obsession) — status, type, priority 가 모두 int.
 *        status: 0=임시저장, 1=상신, 2=승인, 3=반려, 9=취소  (의미가 코드 곳곳에 흩어짐)
 *        type:   1=지출, 2=휴가, 3=구매, 4=기타
 *        priority: 1=낮음, 2=보통, 3=높음
 * [스멜] 캡슐화 부재 — 모든 필드에 public setter. 누구나 상태를 마음대로 바꿀 수 있다.
 *
 * [리팩토링] status(int) → {@link ApprovalStatus} enum으로 전환. DB 컬럼·API 응답(JSON)의
 *            정수값은 {@link ApprovalStatusConverter}·{@link ApprovalStatus#getCode()}로
 *            기존과 동일하게 유지된다(자세한 이유는 ApprovalStatus 클래스 주석 참고).
 *            type·priority는 이번 변경 범위 밖이라 아직 int 그대로다.
 * [리팩토링] ApprovalService의 if-지옥에 있던 상태 전이·권한 규칙(submit/approve/reject/cancel)을
 *            이 엔티티의 도메인 메서드로 이동했다(기법: Move Method — Anemic Domain Model 해소).
 *            레거시는 규칙 위반 시 예외 없이 조용히 무시했는데, 그 동작을 그대로 보존하기 위해
 *            각 메서드는 예외를 던지지 않고 성공 여부를 {@code boolean}으로 반환한다 — 서비스는
 *            반환값이 false면 저장·알림·감사로그를 생략해 기존과 동일하게 "조용한 무시"를 유지한다.
 */
@Entity
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String content;
    private int type;       // 1=지출 2=휴가 3=구매 4=기타 (의미를 주석으로만 설명 → enum 후보)
    @Convert(converter = ApprovalStatusConverter.class)
    private ApprovalStatus status;   // [리팩토링] int(0/1/2/3/9) → enum. DB엔 정수 그대로 저장(Converter).
    private int priority;   // 1=낮음 2=보통 3=높음
    private Long drafterId;     // 기안자
    private Long approverId;    // 결재자
    private String rejectReason;
    private long amount;        // 지출/구매 금액
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public Long getDrafterId() { return drafterId; }
    public void setDrafterId(Long drafterId) { this.drafterId = drafterId; }
    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // [매직넘버 제거] ApprovalService에 있던 상수를 규칙과 함께 이 엔티티로 옮겨왔다.
    private static final int EXPENSE_TYPE_CODE = 1;
    private static final long AUTO_PRIORITY_UPGRADE_AMOUNT_THRESHOLD = 1_000_000L;
    private static final int HIGH_PRIORITY_CODE = 3;
    private static final int MIN_ROLE_CODE_FOR_APPROVAL = 2;

    /**
     * 상신: 임시저장 상태일 때만 가능(기안자 본인 확인은 레거시에도 없다 — 동작 보존).
     * 지출(EXPENSE_TYPE_CODE)이고 금액이 임계값 이상이면 우선순위를 자동으로 높인다.
     *
     * @return 상태가 실제로 바뀌었으면 true, 전제조건 위반으로 무시됐으면 false
     */
    public boolean submit() {
        if (status != ApprovalStatus.DRAFT) {
            return false;
        }
        if (type == EXPENSE_TYPE_CODE && amount >= AUTO_PRIORITY_UPGRADE_AMOUNT_THRESHOLD) {
            priority = HIGH_PRIORITY_CODE;
        }
        status = ApprovalStatus.SUBMITTED;
        updatedAt = LocalDateTime.now();
        return true;
    }

    /** 승인: 상신 상태 + 본인이 결재자 + 권한(팀장 이상) 일 때만. */
    public boolean approve(User actor) {
        if (status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (approverId == null || !approverId.equals(actor.getId())) {
            return false;
        }
        if (actor.getRole() < MIN_ROLE_CODE_FOR_APPROVAL) {
            return false;
        }
        status = ApprovalStatus.APPROVED;
        updatedAt = LocalDateTime.now();
        return true;
    }

    /** 반려: 승인과 전제조건이 완전히 동일하다(상신 상태 + 본인이 결재자 + 권한). */
    public boolean reject(User actor, String reason) {
        if (status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (approverId == null || !approverId.equals(actor.getId())) {
            return false;
        }
        if (actor.getRole() < MIN_ROLE_CODE_FOR_APPROVAL) {
            return false;
        }
        status = ApprovalStatus.REJECTED;
        rejectReason = reason;
        updatedAt = LocalDateTime.now();
        return true;
    }

    /** 취소: 기안자 본인 + 아직 승인 전(임시저장 또는 상신)일 때만. 승인 권한(role)은 확인하지 않는다. */
    public boolean cancel(User actor) {
        if (status != ApprovalStatus.DRAFT && status != ApprovalStatus.SUBMITTED) {
            return false;
        }
        if (drafterId == null || !drafterId.equals(actor.getId())) {
            return false;
        }
        status = ApprovalStatus.CANCELED;
        updatedAt = LocalDateTime.now();
        return true;
    }
}
