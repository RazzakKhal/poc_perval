package com.pervalpoc.demo.captcha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Service
public class FriendlyCaptchaService {

    private static final Logger log = LoggerFactory.getLogger(FriendlyCaptchaService.class);

    private final RestClient restClient;
    private final FriendlyCaptchaProperties properties;

    public FriendlyCaptchaService(
            FriendlyCaptchaProperties properties,
            @Value("${app.proxy.host}") String proxyHost,
            @Value("${app.proxy.port}") int proxyPort) {
        this.properties = properties;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean verify(String captchaResponse) {
        if (captchaResponse == null || captchaResponse.isBlank()) {
            return false;
        }

        try {
            FriendlyCaptchaVerifyRequest body = new FriendlyCaptchaVerifyRequest(
                    captchaResponse,
                    properties.getSiteKey()
            );

            FriendlyCaptchaVerifyResponse response = restClient.post()
                    .uri(properties.getVerifyUrl())
                    .header("X-API-Key", properties.getApiKey())
                    .body(body)
                    .retrieve()
                    .body(FriendlyCaptchaVerifyResponse.class);

            return response != null && response.isSuccess();

        } catch (Exception e) {
            log.error("Friendly Captcha API call failed", e);
            return false;
        }
    }
}
