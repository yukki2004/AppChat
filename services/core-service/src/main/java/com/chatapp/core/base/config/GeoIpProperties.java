package com.chatapp.core.base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.geoip")
public class GeoIpProperties {

    /** Path to a MaxMind GeoLite2-City .mmdb file, refreshed monthly by an external cron job
     *  (see GeoIpService) — not bundled in the jar since it needs a MaxMind license key and
     *  goes stale. Blank/missing file disables geo lookup entirely: login_country/login_city
     *  stay null rather than the service failing to start (dev/local doesn't need real geo
     *  data, and a stale/missing file in prod shouldn't take login down with it). */
    private String mmdbPath = "";
}
