package com.obsidian.b2c;

import com.obsidian.b2c.CodeMailer.Purpose;
import com.obsidian.b2c.GoogleTokenVerifier.GoogleIdentity;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B2C sign-up and sign-in: name + email + password with an emailed code, or
 * Google. An email/password account can't sign in until its email is verified.
 */
@Service
public class ConsumerAuthService {

    private final ConsumerRepository consumers;
    private final EmailCodeService codes;
    private final GoogleTokenVerifier google;
    private final PasswordEncoder encoder;
    /** Hashed once so unknown emails cost the same Argon2 work as real ones. */
    private final String timingDummy;

    public ConsumerAuthService(ConsumerRepository consumers, EmailCodeService codes, GoogleTokenVerifier google,
            PasswordEncoder encoder) {
        this.consumers = consumers;
        this.codes = codes;
        this.google = google;
        this.encoder = encoder;
        this.timingDummy = encoder.encode("timing dummy for unknown consumers");
    }

    /**
     * Creates the account (or refreshes a still-unverified one: whoever registered it
     * never proved the email was theirs) and emails a verification code.
     */
    @Transactional
    public Consumer register(String rawName, String rawEmail, String password) {
        String email = normalize(rawEmail);
        String name = requireName(rawName);
        Consumer consumer = consumers.findByEmail(email).orElseGet(() -> new Consumer(email, name));
        if (consumer.isEmailVerified()) {
            throw B2cException.accountExists();
        }
        consumer.setDisplayName(name);
        consumer.setPasswordHash(encoder.encode(password));
        consumer = consumers.save(consumer);
        codes.send(email, name, Purpose.VERIFY_EMAIL, true);
        return consumer;
    }

    @Transactional
    public Consumer verifyEmail(String rawEmail, String code) {
        String email = normalize(rawEmail);
        Consumer consumer = consumers.findByEmail(email).orElseThrow(B2cException::invalidCode);
        codes.verify(email, Purpose.VERIFY_EMAIL, code);
        consumer.setEmailVerified(true);
        return consumers.save(consumer);
    }

    public void resendVerification(String rawEmail) {
        String email = normalize(rawEmail);
        consumers.findByEmail(email).filter(c -> !c.isEmailVerified())
                .ifPresent(c -> codes.send(email, c.getDisplayName(), Purpose.VERIFY_EMAIL, false));
    }

    public Consumer authenticate(String rawEmail, String password) {
        String email = normalize(rawEmail);
        Optional<Consumer> consumer = consumers.findByEmail(email);
        String hash = consumer.map(Consumer::getPasswordHash).orElse(null);
        if (hash == null) {
            encoder.matches(password, timingDummy);
            throw B2cException.invalidCredentials();
        }
        if (!encoder.matches(password, hash)) {
            throw B2cException.invalidCredentials();
        }
        if (!consumer.get().isEmailVerified()) {
            codes.send(email, consumer.get().getDisplayName(), Purpose.VERIFY_EMAIL, true);
            throw B2cException.emailNotVerified();
        }
        return consumer.get();
    }

    /**
     * Signs in with Google, creating the account on first use. An existing account
     * with the same email is linked; if that account was never verified, its
     * password is dropped, since whoever set it never proved they own the email.
     */
    @Transactional
    public Consumer signInWithGoogle(String idToken) {
        GoogleIdentity identity = google.verify(idToken);
        Optional<Consumer> bySubject = consumers.findByGoogleSubject(identity.subject());
        if (bySubject.isPresent()) {
            return bySubject.get();
        }
        Consumer consumer = consumers.findByEmail(identity.email()).orElseGet(() ->
                new Consumer(identity.email(), nameFromGoogle(identity)));
        if (!consumer.isEmailVerified()) {
            consumer.setPasswordHash(null);
            consumer.setEmailVerified(true);
        }
        consumer.setGoogleSubject(identity.subject());
        return consumers.save(consumer);
    }

    /** Always succeeds, so the response doesn't reveal whether an account exists. */
    public void forgotPassword(String rawEmail) {
        String email = normalize(rawEmail);
        consumers.findByEmail(email)
                .ifPresent(c -> codes.send(email, c.getDisplayName(), Purpose.RESET_PASSWORD, true));
    }

    @Transactional
    public Consumer resetPassword(String rawEmail, String code, String newPassword) {
        String email = normalize(rawEmail);
        Consumer consumer = consumers.findByEmail(email).orElseThrow(B2cException::invalidCode);
        codes.verify(email, Purpose.RESET_PASSWORD, code);
        consumer.setPasswordHash(encoder.encode(newPassword));
        consumer.setEmailVerified(true); // the code proved they read this mailbox
        return consumers.save(consumer);
    }

    private static String nameFromGoogle(GoogleIdentity identity) {
        if (identity.name() != null && !identity.name().isBlank()) {
            return truncate(identity.name().trim());
        }
        return identity.email().substring(0, identity.email().indexOf('@'));
    }

    private static String requireName(String raw) {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw B2cException.badRequest("Enter your name.");
        }
        return truncate(name);
    }

    private static String truncate(String name) {
        return name.length() <= 120 ? name : name.substring(0, 120);
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
