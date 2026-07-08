# 종합 스멜 목록 (smells.md)

`docs/4-4`~`4-9` 진단을 종합한 스멜 마스터 카탈로그. 심각도순 요약은 `docs/4-10. 도출 스멜 - 심각도
표.md`를 참고하고, 이 문서는 각 스멜의 **전체 발생 위치**를 빠짐없이 모은 참조용 목록이다.

## 1. 테스트 부재 (No Tests) — 심각도: 치명

- `src/test/java` 하위에 테스트 클래스 0개. `ApprovalService`/`NoticeService`/`ScheduleService`
  전체가 안전망 없이 운영되고 있다.

## 2. God Class — 심각도: 치명

- `ApprovalService` 전체 — 검증 + 영속화 + 메일 발송 + 감사 로그 + 상태 라벨 포맷팅 + 권한 판정을
  한 클래스가 전담(클래스 상단 주석에 스스로 "이 과정의 주인공 안티패턴"이라 명시).

## 3. Long Method · 깊은 중첩 — 심각도: 높음

- `ApprovalService.processApproval()` L75-160 — 86줄, 최대 중첩 깊이 6단계(승인/반려 분기).
- (참고, 50줄 미만이지만 중첩이 깊음) `NoticeService.publish()` L49-73 — 25줄, 깊이 5.
- (참고) `ScheduleService.create()` L27-65 — 39줄, 깊이 4.

## 4. Magic Number / Primitive Obsession — 심각도: 높음

- `Approval.status` (0=임시저장·1=상신·2=승인·3=반려·9=취소) — `Approval.java` L26, `ApprovalService.java`
  L53,86,92,97,110-112,115,129,131,134,149,151,153,170,172-176
- 결재 처리 액션 파라미터(`action`/`proc`, 1=상신·2=승인·3=반려·9=취소) — `ApprovalController.java`
  L36,42, `ApprovalService.java` L73,87,90,110,129,149
- `Approval.type` (1=지출·2=휴가·3=구매·4=기타) — `Approval.java` L25, `ApprovalController.java` L27,
  `ApprovalService.java` L94
- `Approval.priority` (1=낮음·2=보통·3=높음) — `Approval.java` L27, `ApprovalController.java` L28,
  `ApprovalService.java` L52,95
- 금액 임계값(100,000/1,000,000/10,000,000) — `ApprovalService.java` L94,184-187
- `Notice.status` (0=임시·1=게시·9=내림) — `Notice.java` L19, `NoticeService.java` L37,57,58,77-80
- `Notice.category` (1=일반·2=긴급·3=인사) — `Notice.java` L20, `NoticeService.java` L36,62
- `Schedule.status` (0=예정·1=확정·9=취소) — `Schedule.java` L18, `ScheduleService.java` L42,58,73,74
- `User.role` (1=사원·2=팀장·3=임원) — `User.java` L19, `DataSeeder.java` L24-27

## 5. Duplicated Code — 심각도: 중상

- **A. 감사 로그 기록 포맷** — `ApprovalService.java` L62-65,164-165(부분 추출) / `NoticeService.java`
  L44-45,69-70 / `ScheduleService.java` L62-63,76-77
- **B. 협력 객체 직접 `new`** — (Tight Coupling 항목과 동일 위치, 아래 참고)
- **C. 메일 본문 생성+발송** — `ApprovalService.java` L103-107(SUBMIT),121-124(APPROVE),140-144(REJECT) /
  `NoticeService.java` L64-67(긴급 공지)
- **D. 조회 + null 체크 후 조용히 리턴** — `ApprovalService.java` L76-84 / `NoticeService.java` L50-53 /
  `ScheduleService.java` L69-71
- **E. 권한 판정(`role>=2`) 조건식** — `ApprovalService.java` L114(APPROVE),133(REJECT, 같은 클래스
  내부 중복) / `NoticeService.java` L56
- **F. 상태 라벨 변환(`statusLabel`) if/else 사다리** — `ApprovalService.java` L169-179(5분기) /
  `NoticeService.java` L76-82(3분기)

## 6. Tight Coupling (직접 `new`) — 심각도: 중간

- `ApprovalService.java` L37-38 — `new SmtpMailSender()`, `new FileAuditLogger()`
- `NoticeService.java` L24-25 — `new SmtpMailSender()`, `new FileAuditLogger()`
- `ScheduleService.java` L20 — `new FileAuditLogger()`
- (참고, 강결합 아님) `new Approval()`/`new Notice()`/`new Schedule()`/`new User()`는 영속화용 엔티티
  생성이라 제외

## 7. 캡슐화 부재 (Public Setter 전면 개방) — 심각도: 중간

- `Approval`, `Notice`, `Schedule`, `User` 전체 필드에 public getter/setter — 누구나 상태를 임의로
  변경 가능.

## 8. Feature Envy — 심각도: 중간

- `ApprovalService.amountGrade()` L182-188 — 금액 등급(S/A/B/C) 계산을 서비스가 직접 수행.
- `ApprovalService.processApproval()` L94-96 — 금액 기준(지출 && 100만원 이상) 우선순위 자동 상향
  규칙이 서비스에 위치.

## 9. Long Parameter List — 심각도: 중하

- `ApprovalService.create(title, content, type, priority, drafterId, approverId, amount, urgent)`
  L46-47 — 파라미터 8개.

## 10. Poor Naming (약어 변수) — 심각도: 낮음~중간

- `ApprovalService`: `d`(L48~, Approval 객체), `u`(L81, User 객체), `s`(L86, status), `proc`(L87,
  action), `tmp`(L171, 라벨), `a`(L183, amount)
- `ScheduleService`: `flag1`(L40, 겹침 여부), `s`(L41, 루프 변수), `sc`(L52, Schedule 객체)
- `NoticeService`: `n`(L33, Notice 객체), `u`(L52, User 객체)

## 11. 비대칭/미구현 매직값 (죽은 코드) — 심각도: 낮음~중간

- `Notice.category=3`(인사) — 정의만 있고 분기 처리 없음(`Notice.java` L20)
- `Notice.status=9`(내림) — `statusLabel()` 라벨만 있고 실제 전이 메서드 없음(`NoticeService.java` L80)
- `Schedule.status=9`(취소) — 겹침 검사 제외 조건(`ScheduleService.java` L42)만 있고 취소 처리 메서드
  없음
- `notice`·`schedule` 도메인에 REST Controller 부재 — 서비스 계층까지만 존재, API로 미노출(`4-4` 참고)

## 12. Comment Smell — 심각도: 낮음

- 나쁜 이름을 주석으로 변명하는 패턴 다수 — `ApprovalService.java` L48("d = 결재 문서... 약어라 의미
  불명"), L86,87,171,183 등. Poor Naming(10번)의 부산물.

## 13. 조용한 실패 (Silent Failure) — 심각도: 높음

- `ApprovalService.processApproval()` — 대상 없음(L77-79)/사용자 없음(L82-84)/상태 불일치(L92,112,131,
  151)/권한 없음(L114,133) 시 예외 없이 조용히 `return`.
- `ScheduleService.create()` L30-49 — 제목 누락/시간 오류/시간 겹침 시 원인 구분 없이 전부 `null` 반환.

## 14. 요청 검증 부재 (DTO 없이 Map 캐스팅) — 심각도: 높음

- `ApprovalController.create()` L23-34, `process()` L37-45 — `Map<String,Object>`를 그대로 캐스팅,
  키 철자·타입 오류가 컴파일 타임이 아닌 실행 중 예외로 나타남.

---

심각도순 요약 표는 `docs/4-10. 도출 스멜 - 심각도 표.md` 참고. 모든 항목의 근거 문서는 `docs/4-4`,
`4-5`, `4-7`, `4-8`, `4-9`이며, 리팩토링 시 이 목록의 위치·값은 특성화 테스트로 전후 동일성을
검증한다.
