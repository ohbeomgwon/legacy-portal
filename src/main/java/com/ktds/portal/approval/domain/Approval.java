package com.ktds.portal.approval.domain;

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
}
