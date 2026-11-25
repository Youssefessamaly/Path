package com.internplatform.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated endpoint backing legal-web/support.html's contact form — see
 *  SecurityConfig for the permitAll() entry and SupportRequestService for delivery/anti-abuse. */
@RestController
@RequestMapping("/api/v1/support")
public class SupportRequestController {

    private final SupportRequestService supportRequestService;

    public SupportRequestController(SupportRequestService supportRequestService) {
        this.supportRequestService = supportRequestService;
    }

    @PostMapping("/contact")
    @ResponseStatus(HttpStatus.OK)
    public SupportRequestResponse submitSupportRequest(
            @Valid @RequestBody SupportRequestCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        boolean submitted = supportRequestService.submit(request, clientIp(httpRequest));
        return new SupportRequestResponse(submitted);
    }

    /** Cloudflare always sets CF-Connecting-IP to the real client address, more reliably than
     *  X-Forwarded-For (which it also sets, but as a chain anyone could prepend to before it
     *  reaches Cloudflare's own edge). Falls back for any non-Cloudflare path (local dev). */
    private String clientIp(HttpServletRequest request) {
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp.trim();
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
