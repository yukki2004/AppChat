package com.chatapp.core.auth.service;


public record PendingRegistration(String username, String email, String phone, String passwordHash, String displayName) {
}
