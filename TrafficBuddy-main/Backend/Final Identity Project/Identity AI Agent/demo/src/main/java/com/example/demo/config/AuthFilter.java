package com.example.demo.config;

import com.example.demo.service.AuthService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Signin-before-entry gate (Issue 2). Every request under /api/** must include a valid
 * "Authorization: Bearer <token>" header - except the login endpoint itself and CORS
 * preflight (OPTIONS) requests.
 *
 * Get a token by calling POST /api/auth/login with demo/demo123 (see AuthController /
 * AuthService), then send it as "Authorization: Bearer <token>" on subsequent calls to
 * /api/route/...
 *
 * NOTE: this project has no Spring Security dependency wired up, so this is a plain
 * servlet Filter rather than a SecurityFilterChain - intentionally lightweight for the
 * exhibition build. If your project targets Spring Boot 2.x, change the "jakarta.servlet"
 * imports above to "javax.servlet".
 */
@Component
public class AuthFilter implements Filter {

    private final AuthService authService;

    public AuthFilter(AuthService authService) {
        this.authService = authService;
    }

    private static final String LOGIN_PATH = "/api/auth/login";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();
        String method = req.getMethod();

        // Let CORS preflight requests and the login endpoint itself through unguarded.
        if ("OPTIONS".equalsIgnoreCase(method) || LOGIN_PATH.equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Only guard this app's API endpoints; leave everything else (actuator, static
        // resources, etc.) untouched.
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(req);
        if (authService.isValidToken(token)) {
            chain.doFilter(request, response);
        } else {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.getWriter().write(
                    "{\"error\":\"Please sign in to use Traffic Buddy (demo login: demo / demo123)\"}");
        }
    }

    private String extractToken(HttpServletRequest req) {
        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring("Bearer ".length()).trim();
        }
        return null;
    }
}