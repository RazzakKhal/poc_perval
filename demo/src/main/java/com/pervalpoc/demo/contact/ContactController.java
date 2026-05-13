package com.pervalpoc.demo.contact;

import com.pervalpoc.demo.captcha.FriendlyCaptchaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    private final FriendlyCaptchaService friendlyCaptchaService;

    public ContactController(FriendlyCaptchaService friendlyCaptchaService) {
        this.friendlyCaptchaService = friendlyCaptchaService;
    }

    @PostMapping
    public ResponseEntity<String> submitContact(@RequestBody ContactRequest request) {
        boolean captchaValid = friendlyCaptchaService.verify(request.getCaptchaResponse());

        if (!captchaValid) {
            log.warn("Friendly Captcha validation failed for request from {}", request.getEmail());
            return ResponseEntity.badRequest().body("Captcha invalide ou absent");
        }

        log.info("Contact form accepted for {}", request.getEmail());
        return ResponseEntity.ok("Message reçu");
    }
}
