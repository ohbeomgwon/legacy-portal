package com.ktds.portal.common;

/**
 * [리팩토링] 서비스가 SmtpMailSender를 직접 new 하던 강결합(docs/4-9)을 해소하기 위한 추상화.
 * 구현체(SmtpMailSender)는 생성자 주입으로 연결한다.
 */
public interface MailSender {

    void send(String to, String subject, String body);
}
