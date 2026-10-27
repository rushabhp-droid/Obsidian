package com.obsidian.b2c;

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Sends the 6-digit B2C codes over SMTP (obsidian.b2c.mail, from SMTP_* in .env).
 * Without SMTP the code goes to the log, but only when obsidian.b2c.log-codes is
 * on (DevServer); otherwise sending fails rather than leaking codes into logs.
 */
@Component
public class CodeMailer {

    public enum Purpose { VERIFY_EMAIL, RESET_PASSWORD }

    private static final Logger log = LoggerFactory.getLogger(CodeMailer.class);

    private final B2cProperties properties;
    private final JavaMailSenderImpl sender;

    public CodeMailer(B2cProperties properties) {
        this.properties = properties;
        this.sender = properties.mail().configured() ? sender(properties.mail()) : null;
    }

    public void send(String email, String name, String code, Purpose purpose) {
        if (sender == null) {
            if (!properties.logCodes()) {
                throw new B2cException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED",
                        "This server can't send email yet, so new accounts can't be verified. Try Google sign-in.");
            }
            log.warn("SMTP isn't configured; {} code for {} is {}", purpose, email, code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        String from = properties.mail().from();
        message.setFrom(from == null || from.isBlank() ? properties.mail().username() : from);
        message.setTo(email);
        message.setSubject(code + " is your Obsidian code");
        message.setText("""
                Hi %s,

                %s

                    %s

                The code expires in %d minutes. If you didn't ask for it, you can ignore this email.

                Obsidian""".formatted(name == null || name.isBlank() ? "there" : name,
                purpose == Purpose.VERIFY_EMAIL ? "Enter this code in the Obsidian app to verify your email:"
                        : "Enter this code in the Obsidian app to choose a new password:",
                code, properties.codeTtl().toMinutes()));
        try {
            sender.send(message);
        } catch (MailException e) {
            log.warn("Sending the {} email failed: {}", purpose, e.getMessage());
            throw new B2cException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_FAILED",
                    "We couldn't send the email. Try again in a minute.");
        }
    }

    private static JavaMailSenderImpl sender(B2cProperties.Mail mail) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.host());
        sender.setPort(mail.port());
        sender.setUsername(mail.username());
        sender.setPassword(mail.password());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(mail.username() != null && !mail.username().isBlank()));
        if (mail.port() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        return sender;
    }
}
