package com.obsidian.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A browser's cookies across MockMvc requests: the __Host-session cookie and
 * the XSRF-TOKEN cookie, replayed the way web-dashboard/src/lib/api.ts does.
 */
public final class WebSession {

    private Cookie session;
    private Cookie xsrf;

    public static WebSession signIn(MockMvc mvc, String email, String password) throws Exception {
        WebSession browser = new WebSession();
        MockHttpServletResponse response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"turnstileToken\":null}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        browser.remember(response);
        return browser;
    }

    public void remember(MockHttpServletResponse response) {
        Cookie newSession = response.getCookie("__Host-session");
        if (newSession != null) {
            session = newSession.getMaxAge() == 0 ? null : newSession;
        }
        Cookie newXsrf = response.getCookie("XSRF-TOKEN");
        if (newXsrf != null && !newXsrf.getValue().isEmpty()) {
            xsrf = newXsrf;
        }
    }

    /** Cookies only, like a cross-site form post would carry. */
    public MockHttpServletRequestBuilder cookies(MockHttpServletRequestBuilder request) {
        List<Cookie> cookies = new ArrayList<>();
        if (session != null) {
            cookies.add(session);
        }
        if (xsrf != null) {
            cookies.add(xsrf);
        }
        return cookies.isEmpty() ? request : request.cookie(cookies.toArray(Cookie[]::new));
    }

    /** Cookies plus the X-XSRF-TOKEN header the dashboard sends on mutations. */
    public MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) {
        MockHttpServletRequestBuilder withCookies = cookies(request);
        return xsrf == null ? withCookies : withCookies.header("X-XSRF-TOKEN", xsrf.getValue());
    }

    public Cookie sessionCookie() {
        return session;
    }
}
