package com.ktds.portal.approval.dto;

/**
 * [리팩토링] ApprovalController.process()가 받던 {@code Map<String,Object>}를 record DTO로
 * 교체. JSON 필드명(userId/action/reason)은 레거시와 동일하게 유지한다.
 *
 * 레거시는 {@code body.getOrDefault("reason", "")}로 reason 생략 시 빈 문자열을 사용했다.
 * reason이 그대로 반려 사유(rejectReason)에 저장되고 메일 본문에도 들어가므로(관찰 가능한 동작),
 * compact 생성자에서 null을 ""로 바꿔 이 기본값 동작을 그대로 보존한다.
 */
public record ProcessRequest(Long userId, int action, String reason) {

    public ProcessRequest {
        if (reason == null) {
            reason = "";
        }
    }
}
