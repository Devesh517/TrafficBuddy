package com.example.demo.controller;

import com.example.demo.model.LoginRequest;
import com.example.demo.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Demo login - username: demo, password: demo123
    // On success returns a token that must be sent as "Authorization: Bearer <token>"
    // on every /api/... request (see AuthFilter).
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req == null || req.getUsername() == null || req.getPassword() == null) {
            return ResponseEntity.badRequest().body("username and password must be provided");
        }

        String token = authService.login(req.getUsername(), req.getPassword());
        if (token == null) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }

        Map<String, String> body = new HashMap<>();
        body.put("token", token);
        return ResponseEntity.ok(body);
    }
}