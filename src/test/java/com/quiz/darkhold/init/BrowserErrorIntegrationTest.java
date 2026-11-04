package com.quiz.darkhold.init;

import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.repository.UserRepository;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:browser-errors;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(BrowserErrorIntegrationTest.Failures.class)
class BrowserErrorIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;

    private RequestPostProcessor admin() {
        return user(new DarkholdUserDetails(users.findByEmail("admin@admin.com")));
    }

    @ParameterizedTest
    @CsvSource({"bad-input,400", "forbidden,403", "missing,404", "method,405", "conflict,409",
            "upload,413", "format,415", "rate,429", "crash,500", "unavailable,503"})
    void browserErrorsAreLocalizedHtmlAndApiErrorsRemainJson(final String kind, final int status) throws Exception {
        mvc.perform(get("/test/fail/" + kind).with(admin()).accept(MediaType.TEXT_HTML).param("lang", "de"))
                .andExpect(status().is(status)).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("lang=\"de\"")))
                .andExpect(content().string(containsString("Zurück")))
                .andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAIL"))));
        mvc.perform(get("/test/fail/" + kind).with(admin()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").exists()).andExpect(jsonPath("$.message").exists())
                .andExpect(content().string(not(containsString("PRIVATE_DATABASE_DETAIL"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/registration", "/options", "/admin", "/account", "/userManagement",
            "/user/page/1", "/user/create", "/challenge/create", "/challenge/view", "/gameManagement",
            "/my-active-games", "/past-games", "/join"})
    void mainBrowserPagesRenderWithoutErrors(final String path) throws Exception {
        mvc.perform(get(path).with(admin()).accept(MediaType.TEXT_HTML).param("lang", "de"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @ParameterizedTest
    @CsvSource({"/user/delete/2,405", "/users/2/enabled/false,405", "/user/edit/not-a-number,400",
            "/user/page/0,400", "/edit_challenge/invalid,400", "/no-such-page,404",
            "/images/no-such-image.png,404", "/preconfigure,400"})
    void invalidBrowserRequestsRenderTheirRealStatus(final String path, final int expected) throws Exception {
        mvc.perform(get(path).with(admin()).accept(MediaType.TEXT_HTML))
                .andExpect(status().is(expected)).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/publish", "/preconfigure", "/live/host", "/interstitial"})
    void incompleteBrowserFormsAreBadRequestsInsteadOfInternalErrors(final String path) throws Exception {
        mvc.perform(post(path).with(admin()).with(csrf()).accept(MediaType.TEXT_HTML))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void brokenJsonIsReportedAsJsonAndNotAnInternalError() throws Exception {
        mvc.perform(post("/api/question").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unauthenticatedApiRequestsDoNotRedirectToHtmlLogin() throws Exception {
        mvc.perform(get("/api/question/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .accept(MediaType.TEXT_HTML).param("lang", "de"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Formular ist abgelaufen")));
    }

    @Controller
    static class Failures {
        @GetMapping("/test/fail/{kind}")
        public String fail(@PathVariable final String kind) {
            throw switch (kind) {
                case "bad-input" -> new IllegalArgumentException("Invalid input");
                case "forbidden" -> new org.springframework.security.access.AccessDeniedException("Denied");
                case "missing" -> new ResponseStatusException(HttpStatus.NOT_FOUND);
                case "method" -> new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED);
                case "conflict" -> new DataIntegrityViolationException("PRIVATE_DATABASE_DETAIL");
                case "upload" -> new MaxUploadSizeExceededException(2097152);
                case "format" -> new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                case "rate" -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
                case "unavailable" -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
                default -> new IllegalStateException("PRIVATE_DATABASE_DETAIL");
            };
        }
    }
}
