package com.pervalpoc.demo.captcha;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("friendly-captcha")
public class FriendlyCaptchaProperties {

    private String siteKey;
    private String apiKey;
    private String verifyUrl;

    public String getSiteKey() { return siteKey; }
    public void setSiteKey(String siteKey) { this.siteKey = siteKey; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getVerifyUrl() { return verifyUrl; }
    public void setVerifyUrl(String verifyUrl) { this.verifyUrl = verifyUrl; }
}
