package com.tubereturns.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class SafeImageFetcher {

    public record FetchedImage(byte[] data, String contentType) {}

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    @Value("${tubereturns.youtube.thumbnail-hosts}")
    private List<String> allowedHosts;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public FetchedImage fetch(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Rejected malformed image URL");
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            log.warn("Rejected image URL with disallowed scheme: {}", scheme);
            return null;
        }
        String host = uri.getHost();
        if (host == null || !allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            log.warn("Rejected image URL with host outside the allowlist: {}", host);
            return null;
        }
        if (!isPublicHost(host)) {
            log.warn("Rejected image URL with non-public or missing host");
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                log.warn("Image URL returned status {}", response.statusCode());
                return null;
            }
            byte[] body = response.body();
            if (body == null || body.length == 0 || body.length > MAX_BYTES) {
                log.warn("Image URL returned an unusable body");
                return null;
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
            if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                log.warn("Image URL returned a non-image content type: {}", contentType);
                return null;
            }
            return new FetchedImage(body, contentType);
        } catch (Exception e) {
            log.warn("Failed to fetch image: {}", e.getMessage());
            return null;
        }
    }

    private boolean isPublicHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            log.warn("Could not resolve image host: {}", host);
            return false;
        }
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 169 && b1 == 254) {
                return false;
            }
            if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                return false;
            }
        }
        if (bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC) {
            return false;
        }
        return true;
    }
}
