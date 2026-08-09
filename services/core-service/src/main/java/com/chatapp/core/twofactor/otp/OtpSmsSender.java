package com.chatapp.core.twofactor.otp;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
public class OtpSmsSender {

    public void send(String to, String code) {
        log.info("otp sms send (MOCK, no provider configured) to={} code={}", to, code);
    }
}
