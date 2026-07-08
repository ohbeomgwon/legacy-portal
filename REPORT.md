# REPORT.md — `refactor/start..HEAD` 리팩토링 요약

`refactor/start` 태그(특성화 테스트 6개로 안전망을 확보한 시점, 커밋 `d97d124`)부터 현재 `HEAD`(커밋
`f317346`)까지 결재(`approval`) 도메인에 적용한 리팩토링을 정리한다.

## 포함된 커밋 (3개)

| 커밋 | 내용 |
|---|---|
| `f3a20a5` | 약어 변수 리네임(`d→approval`,`u→actor`,`s→status`,`proc` 제거) + `status`(int)를 `ApprovalStatus` enum으로 전환 |
| `755e8b9` | `processApproval` 매직넘버(action/type/금액임계값/우선순위/권한기준) 제거 + action enum을 `ApprovalStatus.Action`으로 통합 |
| `f317346` | `processApproval` God Method 분해 — 가드 클로즈 + `submit`/`approve`/`reject`/`cancel` 4개 private 메서드 추출 |

## 변경 파일

| 파일 | 상태 | 비고 |
|---|---|---|
| `Approval.java` | 수정 | `status` 필드 타입 `int → ApprovalStatus`, `@Convert` 적용 |
| `ApprovalService.java` | 수정 | 리네임, enum 도입, God Method 분해 (197줄 → 266줄) |
| `ApprovalStatus.java` | **신규** | 상태 enum(DRAFT/SUBMITTED/APPROVED/REJECTED/CANCELED) + 중첩 `Action` enum |
| `ApprovalStatusConverter.java` | **신규** | `AttributeConverter` — DB엔 기존 정수 그대로 저장 |
| `ApprovalServiceProcessApprovalCharacterizationTest.java` | 수정 | 단언문을 `isEqualTo(0)` → `isEqualTo(ApprovalStatus.DRAFT)` 등으로 타입만 갱신(값·의미 동일) |

## 메서드 길이 / 중첩 깊이 변화

`docs/4-7. 긴 메서드 탐지.md`에서 진단한 `processApproval()`(86줄, 최대 중첩 6단계, God Method)이
이번 리팩토링의 핵심 대상이었다.

| 시점 | 메서드 | 줄 수 | 최대 중첩 깊이 |
|---|---|---|---|
| `refactor/start` | `processApproval()` (단일 메서드가 상신·승인·반려·취소 전부 처리) | 86줄 (L75-160) | 6단계 |
| `HEAD` | `processApproval()` (조회 + 디스패치만) | 24줄 (L124-147) | 2단계 |
| `HEAD` | `submit()` | 23줄 | 2단계 |
| `HEAD` | `approve()` | 23줄 | 2단계 (가드 클로즈 3개 + 순차 처리) |
| `HEAD` | `reject()` | 24줄 | 2단계 |
| `HEAD` | `cancel()` | 12줄 | 1단계 |

가장 깊었던 승인/반려 분기(6단계: `if(s==1){if(approverId..){if(role>=2){if(drafter!=null){...}}}}`)가
가드 클로즈 3개의 순차 조기 반환으로 바뀌면서 중첩이 사실상 사라졌다. 전체 파일 줄 수는 197→266줄로
늘었지만(가드 클로즈·메서드 분리·주석 추가로 인한 자연스러운 증가), **개별 메서드당 복잡도는
큰 폭으로 낮아졌다** — 이것이 Extract Method 리팩토링의 전형적인 트레이드오프다.

## 클래스 / 메서드 수 변화

| 항목 | `refactor/start` | `HEAD` | 변화 |
|---|---|---|---|
| `approval` 패키지 클래스 수 | 4 (`Approval`, `ApprovalController`, `ApprovalRepository`, `ApprovalService`) | 6 (+`ApprovalStatus`, +`ApprovalStatusConverter`) | +2 |
| `ApprovalService` 메서드 수 | 7 (생성자, `processApproval`, `writeAudit`, `statusLabel`, `amountGrade`, `myDrafts`, `myInbox`) | 11 (+`submit`,`approve`,`reject`,`cancel`) | +4 |

## 테스트 변화

- `refactor/start` 시점에 이미 `ApprovalServiceProcessApprovalCharacterizationTest`(특성화 테스트 6개)가
  존재했다 — 이 태그 자체가 "리팩토링을 시작하기 전 안전망을 확보한 지점"이기 때문이다.
- `refactor/start..HEAD` 구간 동안 **테스트 개수는 6개로 변화 없음**. 세 커밋 각각에서 6/6 green을
  확인한 뒤 다음 단계로 진행했다(연속적으로 안전망을 유지하며 진행).
- 테스트 파일 자체는 한 번 수정됐는데, `status` 필드 타입이 `int → ApprovalStatus`로 바뀌면서
  단언문 문법을 `isEqualTo(0)` → `isEqualTo(ApprovalStatus.DRAFT)` 식으로 맞춘 것뿐이며, 검증하는
  **관찰 동작·기대값은 전혀 바뀌지 않았다**.

## 매직넘버 → 이름 있는 개념으로 전환

| 레거시 매직넘버 | 리팩토링 후 |
|---|---|
| `status` 0/1/2/3/9 | `ApprovalStatus` enum (DB엔 `AttributeConverter`로 정수 그대로 저장, API 응답도 `@JsonValue`로 정수 그대로 직렬화) |
| action 코드 1/2/3/9 | `ApprovalStatus.Action` enum (processApproval의 `int action` 파라미터는 계약 보존을 위해 그대로 유지, 메서드 내부에서만 enum으로 변환) |
| `type == 1`(지출) | `EXPENSE_TYPE_CODE` 상수 |
| `amount >= 1000000` | `AUTO_PRIORITY_UPGRADE_AMOUNT_THRESHOLD` 상수 |
| `setPriority(3)`(높음) | `HIGH_PRIORITY_CODE` 상수 |
| `role >= 2`(팀장 이상) | `MIN_ROLE_CODE_FOR_APPROVAL` 상수 |

## 보존된 것 (동작 불변 확인)

- `public void processApproval(Long id, Long userId, int action, String reason)` 시그니처 그대로.
- DB 컬럼 저장값은 여전히 정수(`0/1/2/3/9`) — `@Enumerated` 대신 `AttributeConverter` 사용.
- API 응답 JSON의 `status` 필드도 여전히 정수(`@JsonValue`로 직렬화 고정).
- 조용한 실패(대상 없음·사용자 없음·상태 불일치·권한 없음·정의되지 않은 action 값)는 예외 없이
  무시되는 레거시 동작 그대로.
- `submit()`에는 "기안자 본인만 상신 가능"이라는 검증이 레거시에도 없었다는 사실을 그대로 보존(주석
  명시, 임의로 추가하지 않음).

## 아직 남은 스멜 (`docs/4-12` 백로그 기준)

- **God Class**: `ApprovalService`가 여전히 검증·영속화·메일·감사로그·권한판정을 전부 갖고 있다
  (이번엔 `processApproval` 내부 분기만 나눴을 뿐, 책임 자체를 다른 클래스로 옮기지는 않았다).
- **Tight Coupling**: `SmtpMailSender`/`FileAuditLogger` 직접 `new` — 미해결.
- **Duplicated Code**: 감사 로그 포맷, 메일 본문 생성 패턴이 `NoticeService`/`ScheduleService`와
  여전히 중복.
- **요청 검증 부재**: `ApprovalController`가 여전히 `Map<String,Object>`를 그대로 캐스팅.
- `Approval.type`/`priority`, `User.role` 필드 자체는 아직 `int`(이번 범위 밖).

이 항목들은 `docs/4-12. 리팩토링 백로그.md`의 BL-03(강결합 해소), BL-04(중복 제거), BL-06(DTO 도입),
BL-07(캡슐화/Feature Envy) 단계에서 이어서 다룬다.
