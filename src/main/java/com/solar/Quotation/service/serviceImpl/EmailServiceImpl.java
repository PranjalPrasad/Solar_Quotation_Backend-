package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.dto.responsedto.BrandingResponseDto;
import com.solar.Quotation.service.EmailService;
import com.solar.Quotation.service.SettingsProvider;
import com.solar.Quotation.util.ImageTypeDetector;
import jakarta.mail.internet.MimeMessage;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Year;

/**
 * Sends the branded password reset e-mail. The company logo is embedded in the message (cid:companyLogo), so it
 * shows even when the server is only reachable on localhost; the logo is read from the file saved by the
 * Settings > Branding upload (platform_setting BRANDING.logoPath, stored under app.file.storage-dir).
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final String LOGO_CID = "companyLogo";

    /** Used when templates/email/password-reset.html is missing or is not an e-mail template (no {{link}}). */
    private static final String DEFAULT_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Reset your password</title>
            </head>
            <body style="margin:0;padding:0;background:#F7ECE1;font-family:Arial,Helvetica,sans-serif;color:#1F2937;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#F7ECE1;padding:24px 12px;">
              <tr>
                <td align="center">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;background:#FFFFFF;border-radius:14px;border-top:4px solid #541A1A;">
                    <tr>
                      <td align="center" style="padding:28px 28px 8px 28px;">
                        {{logoBlock}}
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:12px 28px 0 28px;">
                        <h2 style="margin:0 0 12px 0;font-size:20px;color:#541A1A;">Reset your password</h2>
                        <p style="margin:0 0 12px 0;font-size:14px;line-height:1.6;">Hello {{name}},</p>
                        <p style="margin:0 0 4px 0;font-size:14px;line-height:1.6;">
                          We received a request to reset the password for your {{company}} account.
                          Click the button below to choose a new one.
                        </p>
                      </td>
                    </tr>
                    <tr>
                      <td align="center" style="padding:24px 28px;">
                        <a href="{{link}}" style="display:inline-block;background:#541A1A;color:#FFFFFF;text-decoration:none;font-size:14px;font-weight:bold;padding:12px 28px;border-radius:8px;">Reset Password</a>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:0 28px 8px 28px;">
                        <p style="margin:0 0 8px 0;font-size:12px;line-height:1.6;color:#6B7280;">
                          This link is valid for {{expiry}} minutes and can be used only once.
                          If the button does not work, copy and paste this address into your browser:
                        </p>
                        <p style="margin:0 0 16px 0;font-size:12px;line-height:1.5;word-break:break-all;"><a href="{{link}}" style="color:#810B38;">{{link}}</a></p>
                        <p style="margin:0 0 4px 0;font-size:12px;line-height:1.6;color:#6B7280;">
                          If you did not request this, you can safely ignore this e-mail. Your password will not change.
                        </p>
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:16px 28px 24px 28px;border-top:1px solid #E9D9C6;">
                        <p style="margin:0;font-size:11px;line-height:1.7;color:#9CA3AF;text-align:center;">
                          {{company}}<br>
                          {{address}}<br>
                          {{phone}} &nbsp; {{email}} &nbsp; {{website}}<br>
                          &copy; {{year}} {{company}}
                        </p>
                      </td>
                    </tr>
                  </table>
                </td>
              </tr>
            </table>
            </body>
            </html>
            """;

    private final JavaMailSender mailSender;
    private final SettingsProvider settings;
    private final Path storageRoot;
    private final String from;
    private final String mailUsername;
    private final String fallbackLogoFile;
    private final boolean devLogLink;
    private final String template;

    public EmailServiceImpl(JavaMailSender mailSender, SettingsProvider settings,
                            @Value("${app.file.storage-dir:./uploads}") String storageDir,
                            @Value("${app.mail.from:}") String from,
                            @Value("${spring.mail.username:}") String mailUsername,
                            @Value("${app.mail.logo-file:}") String fallbackLogoFile,
                            @Value("${app.mail.dev-log-link:false}") boolean devLogLink) {
        this.mailSender = mailSender;
        this.settings = settings;
        this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
        this.from = from == null || from.isBlank() ? mailUsername : from.trim();
        this.mailUsername = mailUsername;
        this.fallbackLogoFile = fallbackLogoFile == null ? "" : fallbackLogoFile.trim();
        this.devLogLink = devLogLink;
        this.template = loadTemplate();
    }

    @Override
    @Async
    public void sendPasswordReset(String toEmail, String toName, String resetLink, long expiryMinutes) {
        if (mailUsername == null || mailUsername.isBlank()) {
            log.warn("Mail is not configured (MAIL_USERNAME / MAIL_PASSWORD). Reset e-mail NOT sent to {}", mask(toEmail));
            if (devLogLink) {
                log.warn("DEV ONLY - reset link for {}: {}", mask(toEmail), resetLink);
            }
            return;
        }
        try {
            BrandingResponseDto b = settings.branding();
            String company = nz(b.getName(), "Solar CRM");
            LogoData logo = loadLogo(b.getLogoPath());
            String name = nz(toName, "there");

            String html = render(company, name, resetLink, expiryMinutes, b, logo != null);
            String text = "Hello " + name + ",\n\n"
                    + "We received a request to reset the password for your " + company + " account.\n"
                    + "Use the link below to choose a new password (valid for " + expiryMinutes + " minutes):\n\n"
                    + resetLink + "\n\n"
                    + "If you did not request this, you can safely ignore this e-mail; your password will not change.\n\n"
                    + company;

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, company);
            helper.setTo(toEmail);
            helper.setSubject("Reset your password - " + company);
            helper.setText(text, html);                       // plain text + HTML alternative
            if (logo != null) {
                helper.addInline(LOGO_CID, new ByteArrayResource(logo.bytes()), logo.contentType());
            }
            mailSender.send(message);
            log.info("Password reset e-mail sent to {}", mask(toEmail));
        } catch (Exception e) {
            // Never surface mail problems to the caller (that would reveal that the account exists).
            log.error("Could not send password reset e-mail to {}: {}", mask(toEmail), e.toString(), e);
            if (devLogLink) {
                log.warn("DEV ONLY - reset link for {}: {}", mask(toEmail), resetLink);
            }
        }
    }

    // ------------------------------------------------------------ template

    private String render(String company, String name, String link, long expiryMinutes,
                          BrandingResponseDto b, boolean hasLogo) {
        String logoBlock = hasLogo
                ? "<img src=\"cid:" + LOGO_CID + "\" alt=\"" + esc(company) + "\" height=\"56\" "
                + "style=\"display:block;margin:0 auto;border:0;height:56px;width:auto;max-width:220px;\">"
                : "<span style=\"font-size:22px;font-weight:700;color:#541A1A;\">" + esc(company) + "</span>";

        return template
                .replace("{{logoBlock}}", logoBlock)
                .replace("{{company}}", esc(company))
                .replace("{{name}}", esc(name))
                .replace("{{link}}", esc(link))
                .replace("{{expiry}}", String.valueOf(expiryMinutes))
                .replace("{{address}}", esc(nz(b.getAddress(), "")))
                .replace("{{phone}}", esc(nz(b.getPhone(), "")))
                .replace("{{email}}", esc(nz(b.getEmail(), "")))
                .replace("{{website}}", esc(nz(b.getWebsite(), "")))
                .replace("{{year}}", String.valueOf(Year.now().getValue()));
    }

    private static String loadTemplate() {
        try (InputStream in = new ClassPathResource("templates/email/password-reset.html").getInputStream()) {
            String t = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            if (t.contains("{{link}}")) {
                return t;
            }
            log.warn("templates/email/password-reset.html has no {{link}} placeholder (wrong file?). "
                    + "Using the built-in e-mail template instead.");
        } catch (IOException e) {
            log.warn("Cannot read templates/email/password-reset.html ({}). Using the built-in e-mail template.",
                    e.getMessage());
        }
        return DEFAULT_TEMPLATE;
    }

    // ------------------------------------------------------------ logo

    private record LogoData(byte[] bytes, String contentType) { }

    private LogoData loadLogo(String brandingLogoPath) {
        LogoData data = null;
        if (brandingLogoPath != null && brandingLogoPath.startsWith("/files/")) {
            Path p = storageRoot.resolve(brandingLogoPath.substring("/files/".length())).normalize();
            if (p.startsWith(storageRoot)) {
                data = read(p);
            }
        }
        if (data == null && !fallbackLogoFile.isEmpty()) {
            data = read(Paths.get(fallbackLogoFile).toAbsolutePath().normalize());
        }
        if (data == null) {
            log.warn("No company logo found for the e-mail (upload one in Settings > Branding, or set app.mail.logo-file). "
                    + "Sending with the company name instead.");
        }
        return data;
    }

    private static final int LOGO_MAX_HEIGHT_PX = 120;     // 2x of the 56 px shown, sharp on retina, small file

    /**
     * Reads the logo and ALWAYS hands the mail a PNG. WebP (and big images) are converted/scaled here, because
     * Outlook desktop cannot show WebP. WebP decoding needs the imageio-webp dependency (see pom.xml.ADD.txt).
     */
    private LogoData read(Path p) {
        try {
            if (!Files.isRegularFile(p) || Files.size(p) > 2L * 1024 * 1024) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(p);
            String ext = ImageTypeDetector.detect(bytes);
            if (ext == null) {
                return null;
            }
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                if ("webp".equals(ext)) {
                    log.warn("Logo {} is WebP but no WebP decoder is installed. Add the imageio-webp dependency "
                            + "or upload a PNG/JPG logo.", p.getFileName());
                    return null;
                }
                return new LogoData(bytes, ImageTypeDetector.contentType(ext));   // cannot decode: send as is
            }
            if (!"webp".equals(ext) && img.getHeight() <= LOGO_MAX_HEIGHT_PX) {
                return new LogoData(bytes, ImageTypeDetector.contentType(ext));   // png/jpg already small
            }
            return new LogoData(toPng(img), "image/png");
        } catch (Exception e) {
            log.warn("Cannot read logo {}: {}", p, e.getMessage());
            return null;
        }
    }

    private static byte[] toPng(BufferedImage src) throws IOException {
        int h = Math.min(src.getHeight(), LOGO_MAX_HEIGHT_PX);
        int w = Math.max(1, Math.round(src.getWidth() * (h / (float) src.getHeight())));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bos);
        return bos.toByteArray();
    }

    // ------------------------------------------------------------ small helpers

    private static String esc(String s) { return HtmlUtils.htmlEscape(s == null ? "" : s); }

    private static String nz(String s, String fallback) { return s == null || s.isBlank() ? fallback : s.trim(); }

    private static String mask(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}