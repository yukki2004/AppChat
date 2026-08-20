package com.chatapp.core.exception.common;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** {@code code} is for internal reference/logging ONLY — the HTTP response still returns
 *  {@code name()} as the String {@code ApiError.code} client sees, same format every other
 *  exception handler in {@link com.chatapp.core.exception.GlobalExceptionHandler} already uses
 *  (e.g. {@code "USER_ALREADY_EXISTS"}). Never expose the int {@code code} in a response body. */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
public enum ErrorCode {

    // friend/#19-20 (POST /friends/requests, /friends/requests/{userId}/accept)
    SELF_FRIEND_REQUEST_NOT_ALLOWED(2001, "You cannot send a friend request to yourself", HttpStatus.BAD_REQUEST),
    REQUESTER_NOT_FOUND(2002, "Requester not found", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(2003, "User not found", HttpStatus.NOT_FOUND),
    FRIEND_REQUEST_NOT_ALLOWED(2004, "You cannot send a friend request to this user", HttpStatus.FORBIDDEN),
    ALREADY_FRIENDS(2005, "You are already friends with this user", HttpStatus.CONFLICT),
    FRIEND_REQUEST_ALREADY_SENT(2006, "You have already sent a friend request to this user", HttpStatus.CONFLICT),
    FRIENDSHIP_ALREADY_EXISTS(2007, "A friendship already exists with this user", HttpStatus.CONFLICT),
    FRIEND_REQUEST_NOT_FOUND(2008, "No pending friend request from this user", HttpStatus.NOT_FOUND),

    // friend/#21-22 (DELETE /friends/requests/{userId}, DELETE /friends/{userId})
    FRIENDSHIP_NOT_FOUND(2011, "You are not friends with this user", HttpStatus.NOT_FOUND),

    // block/#24-25 (POST /blocks, DELETE /blocks/{userId})
    SELF_BLOCK_NOT_ALLOWED(2012, "You cannot block yourself", HttpStatus.BAD_REQUEST),

    // friend/#26 (POST/DELETE /friends/close/{userId})
    SELF_CLOSE_FRIEND_NOT_ALLOWED(2013, "You cannot add yourself as a close friend", HttpStatus.BAD_REQUEST),

    // friend/QR (GET /friends/qr-token, POST /friends/requests/qr, DELETE /friends/qr-token)
    FRIEND_QR_TOKEN_INVALID(2014, "This QR code is invalid or has expired", HttpStatus.NOT_FOUND),
    FRIEND_QR_TOKEN_USAGE_LIMIT_REACHED(2015, "This QR code has reached its usage limit", HttpStatus.TOO_MANY_REQUESTS),

    // auth/password #11-12 (PUT /auth/password, POST /auth/password/forgot/verify, /auth/password/reset)
    INVALID_OLD_PASSWORD(3001, "Current password is incorrect", HttpStatus.BAD_REQUEST),
    PASSWORD_RESET_CODE_INVALID(3002, "Invalid or expired code", HttpStatus.BAD_REQUEST),
    RESET_TOKEN_INVALID(3003, "Invalid or expired reset token", HttpStatus.UNAUTHORIZED),

    // auth/login (POST /auth/login) — 5 consecutive wrong passwords -> 30 min lockout
    ACCOUNT_TEMP_LOCKED(3004, "Too many failed login attempts — try again in 30 minutes", HttpStatus.TOO_MANY_REQUESTS),

    // auth/register (POST /auth/register/otp, /auth/register/verify)
    USERNAME_ALREADY_EXISTS(4001, "Username already exists", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(4002, "Email is already registered", HttpStatus.CONFLICT),
    PHONE_ALREADY_EXISTS(4003, "Phone number is already registered", HttpStatus.CONFLICT),
    REGISTER_CODE_INVALID(4004, "Invalid or expired verification code", HttpStatus.BAD_REQUEST),
    REGISTER_SESSION_EXPIRED(4005, "Registration session expired — start over", HttpStatus.BAD_REQUEST),

    // auth/login + 2FA challenge/submit (POST /auth/login, /auth/login/2fa/challenge, /auth/login/2fa)
    INVALID_CREDENTIALS(4011, "Invalid username/email/phone or password", HttpStatus.UNAUTHORIZED),
    ACCOUNT_BLOCKED(4012, "Account is blocked or deactivated", HttpStatus.UNAUTHORIZED),
    PRE_AUTH_TOKEN_INVALID(4013, "Invalid or expired pre_auth_token", HttpStatus.UNAUTHORIZED),
    TWO_FACTOR_CHALLENGE_NOT_STARTED(4014, "Call /auth/login/2fa/challenge first", HttpStatus.UNAUTHORIZED),
    INVALID_BACKUP_CODE(4015, "Invalid backup code", HttpStatus.UNAUTHORIZED),
    INVALID_TWO_FACTOR_CODE(4016, "Invalid verification code", HttpStatus.UNAUTHORIZED),
    BACKUP_CODE_NOT_AVAILABLE(4017, "Backup codes are not available for this account", HttpStatus.BAD_REQUEST),
    ACCOUNT_NOT_VERIFIED(4018, "This account's email/phone has not been verified yet", HttpStatus.UNAUTHORIZED),

    // auth/refresh + auth/sessions (POST /auth/refresh, DELETE /auth/sessions/{id})
    MISSING_REFRESH_TOKEN(4021, "Missing refresh_token", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(4022, "Invalid or expired refresh_token", HttpStatus.UNAUTHORIZED),
    SESSION_NOT_FOUND(4023, "Session not found or already revoked", HttpStatus.NOT_FOUND),

    // twofactor/ settings (2FA setup/confirm/disable/backup-codes)
    NO_PENDING_TOTP_SETUP(5001, "No pending TOTP setup — call /2fa/totp/setup first", HttpStatus.UNAUTHORIZED),
    INVALID_PASSWORD(5002, "Invalid password", HttpStatus.UNAUTHORIZED),
    TWO_FACTOR_METHOD_NOT_ENABLED(5003, "This 2FA method is not enabled for this account", HttpStatus.BAD_REQUEST),
    TWO_FACTOR_METHOD_ALREADY_ENABLED(5004, "This 2FA method is already enabled for this account", HttpStatus.CONFLICT),

    // twofactor/otp (shared OTP attempt lockout, e.g. email 2FA / password reset code)
    OTP_LOCKED(6001, "Too many failed attempts — try again in a few minutes", HttpStatus.TOO_MANY_REQUESTS),
    OTP_RESEND_TOO_SOON(6002, "A code was already sent — wait before requesting another", HttpStatus.TOO_MANY_REQUESTS),

    // auth/oauth (POST /auth/oauth/{provider}/callback)
    OAUTH_PROVIDER_UNSUPPORTED(7001, "Unsupported OAuth provider", HttpStatus.BAD_REQUEST),
    OAUTH_CODE_EXCHANGE_FAILED(7002, "Failed to exchange authorization code with provider", HttpStatus.UNAUTHORIZED),
    OAUTH_EMAIL_ALREADY_REGISTERED(7003,
            "This email is already registered — sign in with your original method instead", HttpStatus.CONFLICT),

    // auth/password/set (POST /auth/password/set) — first-time password for OAuth-only accounts
    PASSWORD_ALREADY_SET(7004, "Password is already set — use PUT /auth/password to change it", HttpStatus.CONFLICT),
    // twofactor/ setup (POST /2fa/totp/setup, /2fa/email/setup)
    PASSWORD_REQUIRED_BEFORE_2FA(7005,
            "Set a password first (POST /auth/password/set) before enabling 2FA", HttpStatus.BAD_REQUEST),

    // auth/link (POST /auth/link/otp, /auth/link/verify) — add email/phone to an account that
    // registered with only the other one
    LINK_TARGET_ALREADY_SET(8001, "This account already has that identifier set", HttpStatus.CONFLICT),
    LINK_CODE_INVALID(8002, "Invalid or expired verification code", HttpStatus.BAD_REQUEST),

    // auth/qrlogin (POST /auth/qr-login/init, GET .../device-info, POST .../confirm, .../claim)
    QR_LOGIN_SESSION_NOT_FOUND(9001, "Invalid or expired QR login session", HttpStatus.UNAUTHORIZED),
    QR_LOGIN_ALREADY_APPROVED(9002, "This QR login session was already approved", HttpStatus.CONFLICT),
    QR_LOGIN_NOT_APPROVED_YET(9003, "This QR login session has not been approved yet", HttpStatus.UNAUTHORIZED),

    // group/#1 (POST /groups)
    GROUP_MIN_MEMBERS_NOT_MET(10002,
            "A group needs at least 3 members including you", HttpStatus.BAD_REQUEST),

    // group/#2, #3-5 (PATCH /groups/{groupId}, POST /groups/{groupId}/qr-code, .../invite-link)
    GROUP_NOT_FOUND(10003, "Group not found", HttpStatus.NOT_FOUND),
    GROUP_NOT_A_MEMBER(10004, "You are not a member of this group", HttpStatus.FORBIDDEN),
    GROUP_PERMISSION_DENIED(10005, "You don't have permission to do this in this group", HttpStatus.FORBIDDEN),

    // group/#6,8,9 (POST /groups/join, /groups/{groupId}/members, join-requests approve/reject)
    GROUP_INVITE_INVALID(10006, "This QR code or invite link is invalid or has expired", HttpStatus.NOT_FOUND),
    GROUP_MEMBER_LIMIT_REACHED(10007, "This group has reached its member limit", HttpStatus.CONFLICT),
    GROUP_ALREADY_MEMBER(10008, "This user is already a member of this group", HttpStatus.CONFLICT),
    GROUP_JOIN_REQUEST_NOT_FOUND(10009, "Join request not found or already reviewed", HttpStatus.NOT_FOUND),

    // group/#10-12 (DELETE /groups/{groupId}/members/{userId}, POST|DELETE .../admins/{userId})
    GROUP_TARGET_NOT_A_MEMBER(10010, "This user is not a member of this group", HttpStatus.NOT_FOUND),
    GROUP_CANNOT_KICK_OWNER_OR_ADMIN(10011,
            "You cannot kick the group owner or another admin", HttpStatus.FORBIDDEN),
    GROUP_TARGET_ALREADY_ADMIN(10012, "This user is already an admin of this group", HttpStatus.CONFLICT),
    GROUP_TARGET_NOT_AN_ADMIN(10013, "This user is not an admin of this group", HttpStatus.NOT_FOUND),

    // group/#13 (PUT /groups/{groupId}/owner)
    GROUP_SELF_TRANSFER_NOT_ALLOWED(10014,
            "You are already the owner of this group", HttpStatus.BAD_REQUEST),
    GROUP_CONCURRENT_MODIFICATION(10015,
            "This group is being modified by someone else — try again", HttpStatus.CONFLICT),

    // group/#14-15 (POST /groups/{groupId}/leave, DELETE /groups/{groupId})
    GROUP_OWNER_CANNOT_LEAVE(10016,
            "Transfer ownership to someone else before leaving this group", HttpStatus.CONFLICT),

    // group/#16 (GET /groups/{groupId}/members)
    GROUP_INVALID_CURSOR(10017, "Invalid pagination cursor", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode httpStatusCode;

    ErrorCode(int code, String message, HttpStatusCode httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }
}
