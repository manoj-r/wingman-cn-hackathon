package io.conferencewingman.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RequestSecurityFilterTest {
    @Test void decodedAndSemicolonPathsRemainInsideOriginProtection() throws Exception {
        var filter = filter(10, "");
        for (String path : new String[] {"/api%2Fv1%2Fconferences", "/api/v1/conferences;ignored=true"}) {
            var response = invoke(filter, path, "203.0.113.1", null, "https://attacker.example", new AtomicInteger());
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("ORIGIN_NOT_ALLOWED");
        }
    }

    @Test void semicolonPathUsesNormalizedRouteForRateLimiting() throws Exception {
        var filter = filter(1, "");
        var calls = new AtomicInteger();
        assertThat(invoke(filter, "/api%2Fv1%2Fconferences", "203.0.113.1", null, null, calls).getStatus())
            .isEqualTo(200);
        var limited = invoke(filter, "/api/v1/conferences", "203.0.113.1", null, null, calls);
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(calls).hasValue(1);
    }

    @Test void encodedApiPathRemainsInsideBodySizeProtection() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api%2Fv1%2Fconferences");
        request.setServletPath("/api%2Fv1%2Fconferences");
        request.setContent(new byte[16 * 1024 + 1]);
        var response = new MockHttpServletResponse();
        filter(10, "").doFilter(request, response, (req, res) -> { });
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("BODY_TOO_LARGE");
    }

    @Test void forwardedIdentityIsUsedOnlyForExplicitlyTrustedPeers() throws Exception {
        var untrustedFilter = filter(1, "");
        assertThat(invoke(untrustedFilter, "/api/v1/conferences", "10.0.0.8", "198.51.100.1", null,
                new AtomicInteger()).getStatus()).isEqualTo(200);
        assertThat(invoke(untrustedFilter, "/api/v1/conferences", "10.0.0.8", "198.51.100.2", null,
                new AtomicInteger()).getStatus()).isEqualTo(429);

        var trustedFilter = filter(1, "10.0.0.8");
        assertThat(invoke(trustedFilter, "/api/v1/conferences", "10.0.0.8", "198.51.100.1", null,
                new AtomicInteger()).getStatus()).isEqualTo(200);
        assertThat(invoke(trustedFilter, "/api/v1/conferences", "10.0.0.8", "198.51.100.2", null,
                new AtomicInteger()).getStatus()).isEqualTo(200);

        var appendProxyFilter = filter(1, "10.0.0.8");
        assertThat(invoke(appendProxyFilter, "/api/v1/conferences", "10.0.0.8",
                "203.0.113.200, 198.51.100.3", null, new AtomicInteger()).getStatus()).isEqualTo(200);
        assertThat(invoke(appendProxyFilter, "/api/v1/conferences", "10.0.0.8",
                "203.0.113.201, 198.51.100.3", null, new AtomicInteger()).getStatus()).isEqualTo(429);
    }

    @Test void newClientsAreAdmittedAfterTheWindowMapReachesCapacity() throws Exception {
        var filter = filter(5000, "10.0.0.8");
        var calls = new AtomicInteger();
        for (int i = 1; i <= 4096; i++) {
            var response = invoke(filter, "/api/v1/conferences", "10.0.0.8", "198.51."+(i / 256)+"."+(i % 256), null, calls);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        var admitted = invoke(filter, "/api/v1/conferences", "10.0.0.8", "203.0.113.9", null, calls);
        assertThat(admitted.getStatus()).isEqualTo(200);
        assertThat(calls).hasValue(4097);
    }

    private static RequestSecurityFilter filter(int limit, String trustedProxies) {
        return new RequestSecurityFilter(new ObjectMapper(), mock(RegistrationRepository.class),
            "http://localhost:3000", limit, limit, trustedProxies);
    }

    private static MockHttpServletResponse invoke(RequestSecurityFilter filter, String path, String peer,
            String forwardedFor, String origin, AtomicInteger calls) throws Exception {
        var request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr(peer);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        if (origin != null) request.addHeader("Origin", origin);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
        return response;
    }
}
