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
    FRIEND_REQUEST_COOLDOWN(2009, "You must wait before sending another request to this user", HttpStatus.TOO_MANY_REQUESTS),
    FRIEND_REQUEST_RATE_LIMIT_EXCEEDED(2010, "You have reached the daily limit for friend requests", HttpStatus.TOO_MANY_REQUESTS),

    // friend/#21-22 (DELETE /friends/requests/{userId}, DELETE /friends/{userId})
    FRIENDSHIP_NOT_FOUND(2011, "You are not friends with this user", HttpStatus.NOT_FOUND),

    // block/#24-25 (POST /blocks, DELETE /blocks/{userId})
    SELF_BLOCK_NOT_ALLOWED(2012, "You cannot block yourself", HttpStatus.BAD_REQUEST),

    // friend/#26 (POST/DELETE /friends/close/{userId})
    SELF_CLOSE_FRIEND_NOT_ALLOWED(2013, "You cannot add yourself as a close friend", HttpStatus.BAD_REQUEST);

    int code;
    String message;
    HttpStatusCode httpStatusCode;

    ErrorCode(int code, String message, HttpStatusCode httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }
}
