package com.solar.Quotation.service;

public interface EmailService {

    /** Sends the branded "reset your password" e-mail. Runs asynchronously and never throws to the caller. */
    void sendPasswordReset(String toEmail, String toName, String resetLink, long expiryMinutes);
}
