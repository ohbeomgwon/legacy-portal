package com.ktds.portal.approval.domain;

/**
 * [리팩토링] ApprovalService.amountGrade()에 있던 금액 등급(S/A/B/C) 계산 로직을 이 enum으로
 * 이동했다(기법: Move Method — 이 로직은 서비스의 책임이 아니라 "금액이라는 도메인 값 자체의
 * 규칙"이라 Feature Envy 스멜이었다). 등급 경계값(1000만/100만/10만원)은 이 클래스 안에서만
 * 쓰이는 상수라 DB에 저장되지 않고, 별도 컨버터도 필요 없다.
 *
 * 반환 문자열("S"/"A"/"B"/"C")은 레거시와 동일하게 enum 상수 이름을 그대로 쓴다.
 */
public enum AmountGrade {
    S, A, B, C;

    private static final long S_THRESHOLD = 10_000_000L;   // 1000만원=S — 기준 숫자의 의미가 코드에 없던 매직넘버였다
    private static final long A_THRESHOLD = 1_000_000L;    // 100만원=A
    private static final long B_THRESHOLD = 100_000L;      // 10만원=B

    public static AmountGrade fromAmount(long amount) {
        if (amount >= S_THRESHOLD) {
            return S;
        } else if (amount >= A_THRESHOLD) {
            return A;
        } else if (amount >= B_THRESHOLD) {
            return B;
        }
        return C;
    }
}
