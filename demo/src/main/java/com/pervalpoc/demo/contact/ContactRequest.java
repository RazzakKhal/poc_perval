package com.pervalpoc.demo.contact;

public class ContactRequest {

    private String name;
    private String email;
    private String message;
    private String captchaResponse;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCaptchaResponse() { return captchaResponse; }
    public void setCaptchaResponse(String captchaResponse) { this.captchaResponse = captchaResponse; }
}
