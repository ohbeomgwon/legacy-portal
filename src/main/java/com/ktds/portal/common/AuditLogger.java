package com.ktds.portal.common;

/**
 * [리팩토링] 서비스가 ConsoleAuditLogger를 직접 new 하던 강결합(docs/4-9)을 해소하기 위한 추상화.
 * 구현체(ConsoleAuditLogger)는 생성자 주입으로 연결한다.
 *
 * [리팩토링] write(String line) → write(action, id, userId)로 계약 변경(기법: Extract Method
 * Duplication을 인터페이스 레벨에서 해소). 타임스탬프 포맷팅 + 로그 라인 조립
 * ("[" + now + "] " + action + " id=" + id + " by=" + userId)이 호출부(ApprovalService/
 * NoticeService/ScheduleService)마다 복붙되어 있던 것을, 이 계약으로 감싸 구현체
 * (ConsoleAuditLogger) 한 곳에서만 조립하도록 옮겼다.
 */
public interface AuditLogger {

    void write(String action, Long id, Long userId);
}
