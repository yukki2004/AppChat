package com.chatapp.core.twofactor.otp;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.config.OtpProperties;

import lombok.RequiredArgsConstructor;

/** Shared across every purpose that emails an OTP code (LOGIN_2FA today; ENABLE_2FA and later
 *  REGISTER/RESET_PASSWORD/CHANGE_EMAIL reuse this unchanged) — same reasoning as
 *  {@link OtpCodeService}: only the trigger differs, not how the code gets delivered. */
@Component
@RequiredArgsConstructor
public class OtpMailSender {

    private final JavaMailSender mailSender;
    private final OtpProperties otpProperties;

    public void send(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(otpProperties.getMailFrom());
        message.setTo(to);
        message.setSubject("Your ChatApp verification code");
        message.setText("Your verification code is: " + code + ". It expires in "
                + (otpProperties.getCodeTtlSeconds() / 60) + " minutes.");
        mailSender.send(message);
    }
}
