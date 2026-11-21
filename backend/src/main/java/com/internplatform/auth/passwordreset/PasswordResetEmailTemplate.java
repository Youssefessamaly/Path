package com.internplatform.auth.passwordreset;

/**
 * Self-contained, client-friendly transactional email: no external images,
 * remote fonts, tracking pixels, links, or other third-party resources.
 */
final class PasswordResetEmailTemplate {

    private PasswordResetEmailTemplate() {
    }

    static String plainText(String code) {
        requireSixDigits(code);
        return "PATH | Password reset\n\n"
                + "Your PATH password reset verification code is:\n" + code + "\n\n"
                + "This code expires 5 minutes after it was requested.\n"
                + "Enter it in the PATH app to continue resetting your password.\n\n"
                + "Never share this code, including with PATH support.\n"
                + "If you didn't request a password reset, you can safely ignore this email.\n\n"
                + "PATH Support | path.app.support@gmail.com";
    }

    static String html(String code) {
        requireSixDigits(code);

        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>PATH password reset</title>
                </head>
                <body style="margin:0;padding:0;background-color:#FAF8F4;color:#16213A;font-family:Arial,Helvetica,sans-serif;">
                  <div style="display:none;font-size:1px;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;">
                    Your PATH verification code expires in 5 minutes.
                  </div>
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%;background-color:#FAF8F4;">
                    <tr><td align="center" style="padding:32px 14px 36px;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%;max-width:540px;">
                        <tr><td style="background-color:#0D4A43;border-radius:22px 22px 0 0;padding:27px 28px 26px;">
                          <span style="font-family:Arial,Helvetica,sans-serif;font-weight:800;font-size:27px;letter-spacing:2px;color:#FFFFFF;">PATH</span>
                          <span style="display:block;margin-top:8px;font-size:12px;letter-spacing:2px;color:#C7E8E2;font-weight:bold;">ACCOUNT SECURITY</span>
                        </td></tr>
                        <tr><td style="background-color:#FFFFFF;border:1px solid #E6E2D8;border-top:0;border-radius:0 0 22px 22px;padding:32px 24px 28px;">
                          <p style="margin:0 0 11px;color:#116259;font-size:12px;font-weight:bold;letter-spacing:1.4px;">PASSWORD RESET</p>
                          <h1 style="margin:0 0 13px;font-size:27px;line-height:1.25;color:#16213A;font-weight:750;">Your verification code</h1>
                          <p style="margin:0 0 27px;font-size:15px;line-height:1.7;color:#5B6472;">
                            We received a request to reset your PATH password. Enter this six-digit code in the app to continue.
                          </p>
                          <!-- Keep the entire six-digit code in one text node so
                               Mail can recognize it as a single one-time code. -->
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="width:100%;border-collapse:collapse;">
                            <tr><td align="center" style="padding:18px 8px;background-color:#E4F3F0;border:1px solid #C7E8E2;border-radius:12px;color:#0D4A43;font-size:30px;line-height:1.25;letter-spacing:6px;font-weight:800;font-family:Arial,Helvetica,sans-serif;">
                              """);
        html.append(code);
        html.append("""
                            </td></tr>
                          </table>
                          <p style="margin:12px 0 22px;text-align:center;color:#5B6472;font-size:12px;line-height:1.6;">Your six-digit PATH verification code</p>
                          <p style="margin:0 0 27px;font-size:13px;line-height:1.6;text-align:center;color:#5B6472;">
                            This code is valid for <strong style="color:#0D4A43;">5 minutes</strong> from when it was requested.
                          </p>
                          <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="border-radius:12px;background-color:#F1FAF7;">
                            <tr><td style="padding:16px 17px;color:#0D4A43;font-size:13px;line-height:1.6;">
                              <strong>Keep your account safe.</strong> Never share your verification code with anyone, including PATH support.
                            </td></tr>
                          </table>
                          <p style="margin:25px 0 0;font-size:13px;line-height:1.7;color:#5B6472;">
                            Didn't request this? You can safely ignore this email. Your password will not change unless the reset is completed.
                          </p>
                        </td></tr>
                        <tr><td align="center" style="padding:23px 8px 0;">
                          <p style="margin:0 0 5px;font-size:12px;line-height:1.6;color:#5B6472;">Sent by PATH Account Support</p>
                          <p style="margin:0;font-size:12px;line-height:1.6;color:#5B6472;">path.app.support@gmail.com</p>
                          <p style="margin:10px 0 0;font-size:11px;line-height:1.5;color:#687384;">This is an automated account-security message.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """);
        return html.toString();
    }

    private static void requireSixDigits(String code) {
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("Password reset code must be six digits");
        }
    }
}
