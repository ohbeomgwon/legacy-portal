package com.ktds.portal.common;

/**
 * [리팩토링] 서비스가 FileAuditLogger를 직접 new 하던 강결합(docs/4-9)을 해소하기 위한 추상화.
 * 구현체(FileAuditLogger)는 생성자 주입으로 연결한다.
 */
public interface AuditLogger {

    void write(String line);
}
