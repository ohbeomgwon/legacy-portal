package com.ktds.portal.common;

import org.springframework.stereotype.Component;

/**
 * 메일 발송기.
 * [리팩토링] {@link MailSender} 인터페이스 구현체로 전환하고 @Component로 등록해, 서비스가
 * 생성자 주입으로 받아 쓰도록 바꿨다(기법: Extract Interface + Dependency Injection).
 * 실제로 콘솔에만 출력하므로 클래스명도 SmtpMailSender → ConsoleMailSender로 정정했다
 * (실제 SMTP 연동은 하지 않는데 이름이 그렇게 암시하는 것 자체가 Poor Naming이었다).
 */
@Component
public class ConsoleMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String body) {
        // 실제로는 JavaMailSender 등을 사용. 실습용으로 콘솔 출력.
        System.out.println("=== MAIL ===");
        System.out.println("TO: " + to);
        System.out.println("SUBJECT: " + subject);
        System.out.println(body);
        System.out.println("============");
    }
}
