package com.ktds.portal.approval;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * [리팩토링] ApprovalStatus enum ↔ DB 정수 매핑. DB 컬럼에는 기존과 동일한 정수(0/1/2/3/9)가
 * 그대로 저장된다 — @Enumerated 대신 AttributeConverter를 쓰는 이유는 CLAUDE.md 불변 규칙
 * ("DB 저장값 변경 금지") 때문이다.
 */
@Converter(autoApply = false)
public class ApprovalStatusConverter implements AttributeConverter<ApprovalStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ApprovalStatus status) {
        return status == null ? null : status.getCode();
    }

    @Override
    public ApprovalStatus convertToEntityAttribute(Integer code) {
        return code == null ? null : ApprovalStatus.fromCode(code);
    }
}
