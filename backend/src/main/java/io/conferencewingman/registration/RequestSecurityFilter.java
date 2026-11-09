package io.conferencewingman.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

@Component
class RequestSecurityFilter extends OncePerRequestFilter {
    private static final int BODY_LIMIT = 16 * 1024;
    private static final UrlPathHelper PATHS = lookupPathHelper();
    private final ObjectMapper json;
    private final RegistrationRepository registrations;
    private final String origin;
    private final int conferenceLimit;
    private final int registrationLimit;
    private final Set<String> trustedProxies;
    private final Map<String, Window> windows = new HashMap<>();
    private record Window(long minute, int count) {}

    RequestSecurityFilter(ObjectMapper json, RegistrationRepository registrations,
            @Value("${PUBLIC_APP_URL:http://localhost:3000}") String publicUrl,
            @Value("${wingman.rate-limit.conferences-per-minute:60}") int conferenceLimit,
            @Value("${wingman.rate-limit.registrations-per-minute:120}") int registrationLimit,
            @Value("${wingman.rate-limit.trusted-proxies:}") String trustedProxies) {
        this.json = json;
        this.registrations = registrations;
        URI base = URI.create(publicUrl);
        this.origin = base.getScheme() + "://" + base.getRawAuthority();
        this.conferenceLimit = conferenceLimit;
        this.registrationLimit = registrationLimit;
        this.trustedProxies = trustedProxies.isBlank() ? Set.of()
            : Arrays.stream(trustedProxies.split(",")).map(String::trim).map(RequestSecurityFilter::normalizeIpLiteral)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = lookupPath(request);
        return !path.startsWith("/api/v1/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = lookupPath(request);
        boolean mutation = Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod());
        if (mutation) {
            String suppliedOrigin = request.getHeader("Origin");
            if ((suppliedOrigin != null && !origin.equals(suppliedOrigin))
                    || "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
                reject(response, 403, "ORIGIN_NOT_ALLOWED", "Same-origin request required."); return;
            }
            String operation = path.equals("/api/v1/conferences") ? "conference"
                : path.matches("/api/v1/conferences/[^/]+/registrations") ? "registration" : null;
            if (operation != null && !allow(operation + ":" + clientAddress(request),
                    operation.equals("conference") ? conferenceLimit : registrationLimit)) {
                response.setHeader("Retry-After", "60");
                reject(response, 429, "RATE_LIMITED", "Please retry shortly."); return;
            }
            byte[] body = request.getInputStream().readNBytes(BODY_LIMIT + 1);
            if (body.length > BODY_LIMIT) {
                reject(response, 400, "BODY_TOO_LARGE", "Request body exceeds 16 KiB."); return;
            }
            request = new BodyRequest(request, body);
        }
        if (path.startsWith("/api/v1/me/")) {
            String token = null;
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if (cookie.getName().equals("wingman_attendee")) token = cookie.getValue();
                }
            }
            try {
                var registration = token != null && token.matches("[A-Za-z0-9_-]{43}")
                    ? registrations.findBySessionTokenHashAndSessionExpiresAtAfter(RegistrationService.hash(token), Instant.now())
                    : Optional.<Registration>empty();
                if (registration.isEmpty()) {
                    reject(response, 401, "UNAUTHORIZED", "Private attendee cookie required."); return;
                }
                request.setAttribute("wingman.registrationId", registration.get().id);
                request.setAttribute("wingman.conferenceId", registration.get().conferenceId);
            } catch (DataAccessException unavailable) {
                response.setHeader("Retry-After", "5");
                reject(response, 503, "DATABASE_UNAVAILABLE", "Please retry shortly."); return;
            }
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String key, int limit) {
        long minute = Instant.now().getEpochSecond() / 60;
        windows.entrySet().removeIf(entry -> entry.getValue().minute() != minute);
        Window previous = windows.get(key);
        int count = previous == null ? 0 : previous.count();
        if (count >= limit) return false;
        if (previous == null && windows.size() >= 4096) {
            windows.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().minute()))
                .ifPresent(oldest -> windows.remove(oldest.getKey()));
        }
        windows.put(key, new Window(minute, count + 1));
        return true;
    }

    private static String lookupPath(HttpServletRequest request) {
        String decoded = PATHS.decodeRequestString(request, PATHS.getPathWithinApplication(request));
        return decoded.replaceAll(";[^/]*", "");
    }

    private static UrlPathHelper lookupPathHelper() {
        UrlPathHelper helper = new UrlPathHelper();
        helper.setUrlDecode(true);
        helper.setRemoveSemicolonContent(true);
        return helper;
    }

    private String clientAddress(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!trustedProxies.contains(peer)) return peer;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return peer;
        String[] chain = forwarded.split(",");
        // Walk from the gateway inward. X-Forwarded-For can contain attacker-supplied
        // values at its left edge when a proxy appends rather than replaces the header.
        for (int i = chain.length - 1; i >= 0; i--) {
            String address = normalizeIpLiteral(chain[i].trim());
            if (address == null) return peer;
            if (!trustedProxies.contains(address)) return address;
        }
        return peer;
    }

    private static String normalizeIpLiteral(String value) {
        try {
            if (value.contains(":") && value.matches("[0-9a-fA-F:.]+"))
                return java.net.InetAddress.getByName(value).getHostAddress();
            if (value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
                for (String octet : value.split("\\.")) if (Integer.parseInt(octet) > 255) return null;
                return java.net.InetAddress.getByName(value).getHostAddress();
            }
        } catch (java.net.UnknownHostException ignored) { }
        return null;
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        json.writeValue(response.getOutputStream(), Map.of("error",
            new ApiErrors.ErrorBody(code, message, Map.of(), UUID.randomUUID().toString())));
    }

    private static class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
            };
        }
        @Override public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
