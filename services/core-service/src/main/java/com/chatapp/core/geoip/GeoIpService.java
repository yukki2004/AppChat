package com.chatapp.core.geoip;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

import org.springframework.stereotype.Component;

import com.chatapp.core.base.config.GeoIpProperties;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Offline IP -> country/city lookup against a self-hosted MaxMind GeoLite2-City .mmdb — never
 * calls a 3rd-party geo API (see `docs/.../system/05-cookie-auth-flow.md` mục E.10 for why:
 * per-login latency, per-lookup cost at scale, and leaking user IPs to an outside service for
 * no reason). City/country-level accuracy only — that's GeoIP's real ceiling, not a shortcut
 * taken here.
 *
 * Same tolerant-degradation philosophy as {@link com.chatapp.core.twofactor.totp.TotpSecretCipher}:
 * a missing/unreadable .mmdb disables geo lookup (every result comes back empty) instead of
 * failing service startup — dev/local has no reason to carry a multi-MB proprietary database
 * file, and a stale file in prod shouldn't be able to take login down with it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeoIpService {

    private final GeoIpProperties properties;
    private DatabaseReader reader;

    @PostConstruct
    public void init() {
        if (properties.getMmdbPath() == null || properties.getMmdbPath().isBlank()) {
            log.warn("No app.geoip.mmdb-path configured — login_country/login_city will stay null.");
            return;
        }
        File mmdbFile = new File(properties.getMmdbPath());
        if (!mmdbFile.isFile()) {
            log.warn("GeoLite2 .mmdb not found at {} — login_country/login_city will stay null.", mmdbFile);
            return;
        }
        try {
            this.reader = new DatabaseReader.Builder(mmdbFile).build();
        } catch (IOException e) {
            log.warn("Failed to load GeoLite2 .mmdb at {} — login_country/login_city will stay null.", mmdbFile, e);
        }
    }

    /** Never throws — any failure (reader not loaded, unparseable IP, IP not found in the
     *  database, e.g. private/reserved ranges in dev) just yields {@link GeoLookupResult#EMPTY}.
     *  A geo lookup failing must never block login. */
    public GeoLookupResult lookup(String ipAddress) {
        if (reader == null || ipAddress == null || ipAddress.isBlank()) {
            return GeoLookupResult.EMPTY;
        }
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            CityResponse response = reader.city(address);
            String country = response.getCountry() != null ? response.getCountry().getIsoCode() : null;
            String city = response.getCity() != null ? response.getCity().getName() : null;
            return new GeoLookupResult(country, city);
        } catch (GeoIp2Exception | IOException e) {
            log.debug("GeoIP lookup failed for {}: {}", ipAddress, e.getMessage());
            return GeoLookupResult.EMPTY;
        }
    }
}
