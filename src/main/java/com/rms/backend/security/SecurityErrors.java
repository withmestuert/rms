package com.rms.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

final class SecurityErrors {
    private SecurityErrors() {}
    static void write(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\"}");
    }
}
