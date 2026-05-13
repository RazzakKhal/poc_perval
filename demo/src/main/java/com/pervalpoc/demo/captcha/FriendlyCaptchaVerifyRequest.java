package com.pervalpoc.demo.captcha;

public record FriendlyCaptchaVerifyRequest(
        String response,
        String sitekey
) {}
