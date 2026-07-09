package com.ktds.portal.approval.dto;

import com.ktds.portal.approval.domain.Approval;

import java.time.LocalDateTime;

/**
 * [리팩토링] 컨트롤러가 {@link Approval} 엔티티를 그대로 응답으로 노출하던 것을 응답 전용 DTO로
 * 분리(기법: Extract DTO / Entity 은닉). 필드명·형식은 레거시 JSON 응답과 100% 동일하게
 * 유지한다 — 특히 status는 {@link com.ktds.portal.approval.domain.ApprovalStatus}가 아니라
 * {@code int}로 담아, 레거시와 동일하게 정수(0/1/2/3/9)로 직렬화되도록 명시했다(CLAUDE.md
 * 응답 형식·DB 값 변경 금지 규칙과 별개로, JSON 계약 자체를 이 DTO가 직접 보증한다).
 */
public record ApprovalResponse(
        Long id,
        String title,
        String content,
        int type,
        int status,
        int priority,
        Long drafterId,
        Long approverId,
        String rejectReason,
        long amount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ApprovalResponse from(Approval approval) {
        return new ApprovalResponse(
                approval.getId(),
                approval.getTitle(),
                approval.getContent(),
                approval.getType(),
                approval.getStatus().getCode(),
                approval.getPriority(),
                approval.getDrafterId(),
                approval.getApproverId(),
                approval.getRejectReason(),
                approval.getAmount(),
                approval.getCreatedAt(),
                approval.getUpdatedAt()
        );
    }
}
