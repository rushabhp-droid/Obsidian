package com.obsidian.b2c;

import com.obsidian.auth.BearerTokenFilter;
import com.obsidian.auth.MobileToken;
import com.obsidian.b2c.B2cDtos.CodeSent;
import com.obsidian.b2c.B2cDtos.EmailRequest;
import com.obsidian.b2c.B2cDtos.GoogleRequest;
import com.obsidian.b2c.B2cDtos.LoginRequest;
import com.obsidian.b2c.B2cDtos.Profile;
import com.obsidian.b2c.B2cDtos.RegisterRequest;
import com.obsidian.b2c.B2cDtos.ResetPasswordRequest;
import com.obsidian.b2c.B2cDtos.SignedIn;
import com.obsidian.b2c.B2cDtos.VerifyEmailRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B2C sign-up and sign-in. Every successful sign-in returns an obc_ bearer token. */
@RestController
@RequestMapping("/api/b2c/v1")
public class ConsumerAuthController {

    private final ConsumerAuthService auth;
    private final ConsumerTokenService tokens;
    private final B2cProperties properties;

    public ConsumerAuthController(ConsumerAuthService auth, ConsumerTokenService tokens, B2cProperties properties) {
        this.auth = auth;
        this.tokens = tokens;
        this.properties = properties;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<CodeSent> register(@Valid @RequestBody RegisterRequest body) {
        Consumer consumer = auth.register(body.name(), body.email(), body.password());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(codeSent(consumer.getEmail()));
    }

    @PostMapping("/auth/verify-email")
    public SignedIn verifyEmail(@Valid @RequestBody VerifyEmailRequest body) {
        return signedIn(auth.verifyEmail(body.email(), body.code()));
    }

    @PostMapping("/auth/resend-code")
    public ResponseEntity<CodeSent> resendCode(@Valid @RequestBody EmailRequest body) {
        auth.resendVerification(body.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(codeSent(ConsumerAuthService.normalize(body.email())));
    }

    @PostMapping("/auth/login")
    public SignedIn login(@Valid @RequestBody LoginRequest body) {
        return signedIn(auth.authenticate(body.email(), body.password()));
    }

    @PostMapping("/auth/google")
    public SignedIn google(@Valid @RequestBody GoogleRequest body) {
        return signedIn(auth.signInWithGoogle(body.idToken()));
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<CodeSent> forgotPassword(@Valid @RequestBody EmailRequest body) {
        auth.forgotPassword(body.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(codeSent(ConsumerAuthService.normalize(body.email())));
    }

    @PostMapping("/auth/reset-password")
    public SignedIn resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        return signedIn(auth.resetPassword(body.email(), body.code(), body.newPassword()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String bearer = BearerTokenFilter.bearerToken(request);
        if (bearer != null) {
            tokens.revoke(bearer);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public Profile me(Consumer consumer) {
        return Profile.of(consumer);
    }

    private SignedIn signedIn(Consumer consumer) {
        MobileToken token = tokens.issue(consumer);
        return new SignedIn(token.token(), token.expiresAt(), Profile.of(consumer));
    }

    private CodeSent codeSent(String email) {
        return new CodeSent(email, properties.codeTtl().toSeconds());
    }
}
