package com.slmtires.itms.controller;

import com.slmtires.itms.security.AppUserPrincipal;
import com.slmtires.itms.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/employee-summary")
    public Map<String, Long> employeeSummary(@AuthenticationPrincipal AppUserPrincipal principal) {
        return dashboardService.employeeSummary(principal.getId());
    }
}
