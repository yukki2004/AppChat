package com.chatapp.core.geoip;

/** {@code country} is the ISO 3166-1 alpha-2 code (VARCHAR(2) in `user_sessions.login_country`),
 *  not the full country name — compact and locale-independent, client maps it to a display
 *  name/flag itself. Either field may be null even on a successful lookup — GeoLite2 sometimes
 *  only resolves country, not city, depending on how granular the IP block's registration data
 *  is. */
public record GeoLookupResult(String country, String city) {

    static final GeoLookupResult EMPTY = new GeoLookupResult(null, null);
}
