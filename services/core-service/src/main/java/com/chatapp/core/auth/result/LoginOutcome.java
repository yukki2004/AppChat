package com.chatapp.core.auth.result;

/** Result of `/auth/login`: either issues real tokens right away ({@link AuthResult}), or
 *  requires a 2FA step first ({@link TwoFactorChallengeResult}). */
public sealed interface LoginOutcome permits AuthResult, TwoFactorChallengeResult {
}
