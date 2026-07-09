package com.ktds.portal.common;

import org.springframework.stereotype.Component;

/**
 * 감사 로그 기록기.
 * [리팩토링] {@link AuditLogger} 인터페이스 구현체로 전환하고 @Component로 등록해, 서비스가
 * 생성자 주입으로 받아 쓰도록 바꿨다(기법: Extract Interface + Dependency Injection). 출력
 * 대상(파일/DB/콘솔)을 바꾸려면 이제 이 구현체만 교체하면 된다.
 */
@Component
public class FileAuditLogger implements AuditLogger {

    @Override
    public void write(String line) {
        // 실제로는 파일에 append. 실습용으로 콘솔 출력.
        System.out.println("[AUDIT] " + line);
    }
}
