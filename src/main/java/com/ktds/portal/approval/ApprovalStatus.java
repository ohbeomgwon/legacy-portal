package com.ktds.portal.approval;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * [리팩토링] 레거시 Approval.status(int 0/1/2/3/9) → enum으로 전환(Replace Magic Number with
 * Symbolic Constant). DB 저장값·API 응답(JSON)은 100% 동일하게 정수를 그대로 유지한다:
 *  - DB: {@link ApprovalStatusConverter}(AttributeConverter)가 enum↔int를 매핑해 컬럼에는
 *        기존과 같은 정수(0/1/2/3/9)가 저장된다. @Enumerated(STRING/ORDINAL)은 쓰지 않는다
 *        (STRING은 문자 저장으로 DB값이 바뀌고, ORDINAL은 선언 순서에 의존해 9와 어긋난다).
 *  - JSON: {@link #getCode()}에 @JsonValue를 붙여 Jackson이 이 enum 필드를 기존과 동일한
 *        정수(예: "status":1)로 직렬화하도록 한다 — 프론트엔드(index.html)는 변경 없이 그대로 동작.
 *
 * 상태 라벨(statusLabel)을 enum이 직접 소유해, 서비스의 5분기 if-else 사다리(레거시
 * ApprovalService.statusLabel)를 제거하고 enum.label() 위임으로 대체한다.
 */
public enum ApprovalStatus {
    DRAFT(0, "임시저장"),
    SUBMITTED(1, "상신"),
    APPROVED(2, "승인"),
    REJECTED(3, "반려"),
    CANCELED(9, "취소");

    private final int code;
    private final String label;

    ApprovalStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String label() {
        return label;
    }

    public static ApprovalStatus fromCode(int code) {
        for (ApprovalStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown ApprovalStatus code: " + code);
    }
}
