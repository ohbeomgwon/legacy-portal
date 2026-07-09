package com.ktds.portal.approval.controller;

import com.ktds.portal.approval.dto.ApprovalResponse;
import com.ktds.portal.approval.dto.CreateApprovalRequest;
import com.ktds.portal.approval.dto.ProcessRequest;
import com.ktds.portal.approval.service.ApprovalService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 결재 REST 컨트롤러.
 *
 * [리팩토링] Map&lt;String,Object&gt;로 파라미터를 받아 그대로 흘려보내던 것을 record DTO
 * (CreateApprovalRequest/ProcessRequest 요청, ApprovalResponse 응답)로 교체했다(기법:
 * Introduce Parameter Object + Extract DTO). 엔드포인트 경로·HTTP 메서드·JSON 필드명·
 * status의 정수 표현은 레거시와 100% 동일하게 유지했다(CLAUDE.md 요청/응답 형식 변경 금지).
 * action(1=상신,2=승인,3=반려,9=취소)이 API에 그대로 노출되는 것도 계약이라 바꾸지 않았다.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @PostMapping
    public ApprovalResponse create(@RequestBody CreateApprovalRequest request) {
        return ApprovalResponse.from(service.create(
                request.title(),
                request.content(),
                request.type(),
                request.priority(),
                request.drafterId(),
                request.approverId(),
                request.amount(),
                request.urgent()
        ));
    }

    // action: 1=상신, 2=승인, 3=반려, 9=취소  ([스멜] 매직넘버를 API 가 그대로 강요 — 계약이라 유지)
    @PostMapping("/{id}/process")
    public void process(@PathVariable Long id, @RequestBody ProcessRequest request) {
        service.processApproval(id, request.userId(), request.action(), request.reason());
    }

    @GetMapping("/drafts/{userId}")
    public List<ApprovalResponse> drafts(@PathVariable Long userId) {
        return service.myDrafts(userId).stream().map(ApprovalResponse::from).toList();
    }

    @GetMapping("/inbox/{userId}")
    public List<ApprovalResponse> inbox(@PathVariable Long userId) {
        return service.myInbox(userId).stream().map(ApprovalResponse::from).toList();
    }
}
