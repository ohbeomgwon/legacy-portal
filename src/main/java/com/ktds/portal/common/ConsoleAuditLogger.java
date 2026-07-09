package com.ktds.portal.common;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 감사 로그 기록기.
 * [리팩토링] {@link AuditLogger} 인터페이스 구현체로 전환하고 @Component로 등록해, 서비스가
 * 생성자 주입으로 받아 쓰도록 바꿨다(기법: Extract Interface + Dependency Injection). 출력
 * 대상(파일/DB/콘솔)을 바꾸려면 이제 이 구현체만 교체하면 된다.
 * 실제로 파일에 쓰지 않고 콘솔에만 출력하므로 클래스명도 FileAuditLogger → ConsoleAuditLogger로
 * 정정했다(파일 저장을 암시하는 이름 자체가 Poor Naming이었다).
 *
 * [리팩토링] 타임스탬프 포맷팅("yyyy-MM-dd HH:mm:ss")과 로그 라인 조립을 각 서비스에서
 * 이 구현체 하나로 옮겨왔다(중복 코드 해소). 출력 형식("[timestamp] ACTION id=.. by=..")은
 * 레거시와 동일하게 유지했다.
 */
@Component
public class ConsoleAuditLogger implements AuditLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void write(String action, Long id, Long userId) {
        // 실제로는 파일에 append. 실습용으로 콘솔 출력.
        String now = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        System.out.println("[AUDIT] [" + now + "] " + action + " id=" + id + " by=" + userId);
    }
}
