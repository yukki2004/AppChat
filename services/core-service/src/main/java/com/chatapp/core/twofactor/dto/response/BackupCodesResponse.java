package com.chatapp.core.twofactor.dto.response;

import java.util.List;

/** Plaintext codes shown exactly once — right after generation (first 2FA method enabled) or
 *  regeneration. Never retrievable again after this response; only the hash is persisted. */
public record BackupCodesResponse(List<String> backupCodes) {
}
