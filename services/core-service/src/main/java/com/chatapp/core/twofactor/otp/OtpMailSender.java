package com.chatapp.core.twofactor.otp;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.config.OtpProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Component
@RequiredArgsConstructor
@Slf4j
public class OtpMailSender {

    private final JavaMailSender mailSender;
    private final OtpProperties otpProperties;


    public void send(String to, String code) {
        log.debug("otp mail send start to={}", to);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(otpProperties.getMailFrom());
        message.setTo(to);
        message.setSubject("Your ChatApp verification code");
        message.setText("Your verification code is: " + code + ". It expires in "
                + (otpProperties.getCodeTtlSeconds() / 60) + " minutes.");
        try {
            mailSender.send(message);
            log.info("otp mail send success to={}", to);
        } catch (MailException e) {
            log.error("otp mail send failed to={}", to, e);
            throw e;
        }
    }
}
