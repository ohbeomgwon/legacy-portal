package com.ktds.portal.approval.dto;

/**
 * [리팩토링] ApprovalController.create()가 받던 {@code Map<String,Object>}를 record DTO로
 * 교체(기법: Introduce Parameter Object / Replace Data Value with Object). JSON 필드명은
 * 레거시(title/content/type/priority/drafterId/approverId/amount/urgent)와 100% 동일하게
 * 유지해 요청 계약을 보존한다(CLAUDE.md 요청 형식 변경 금지).
 *
 * amount·urgent를 원시 타입(long/boolean)으로 선언해, 레거시의
 * {@code body.getOrDefault("amount", 0)} / {@code body.getOrDefault("urgent", false)}와
 * 동일하게 "필드 생략 시 0/false로 기본값 처리"되도록 했다(Jackson은 record 생성자의 원시 타입
 * 파라미터가 JSON에 없으면 타입 기본값을 채운다).
 *
 * title/content/type/priority/drafterId/approverId는 레거시에서도 필수값이라 생략 시 예외가
 * 났다(NullPointerException/ClassCastException) — DTO로 바뀌면서 예외 종류만 Jackson의
 * 역직렬화 오류로 바뀔 뿐, "필수값 없이는 정상 생성 불가"라는 동작의 성격은 동일하다.
 */
public record CreateApprovalRequest(
        String title,
        String content,
        int type,
        int priority,
        Long drafterId,
        Long approverId,
        long amount,
        boolean urgent
) {
}
