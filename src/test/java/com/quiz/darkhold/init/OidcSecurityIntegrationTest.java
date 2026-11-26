package com.quiz.darkhold.init;

import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.repository.UserRepository;
import com.quiz.darkhold.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Single sign-on wired against a static provider registration: no discovery or token call leaves the test. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OidcSecurityIntegrationTest {
    private static final String AUTHORIZE = "https://idp.example.invalid/authorize";
    private static final String CLIENT_ID = "darkhold-test";

    @TestConfiguration
    static class StaticProvider {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration
                    .withRegistrationId(OidcClientConfig.REGISTRATION_ID)
                    .clientId(CLIENT_ID)
                    .clientSecret("secret")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri(AUTHORIZE)
                    .tokenUri("https://idp.example.invalid/token")
                    .jwkSetUri("https://idp.example.invalid/jwks")
                    .userInfoUri("https://idp.example.invalid/userinfo")
                    .userNameAttributeName("sub")
                    .build());
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserService service;
    @Autowired
    private UserRepository users;
    @Autowired
    private BCryptPasswordEncoder encoder;
    @Autowired
    private FindByIndexNameSessionRepository<?> sessions;
    private User me;

    @BeforeEach
    void createPasswordlessOidcUser() {
        User user = new User();
        user.setFirstName("Sso");
        user.setLastName("User");
        user.setEmail("sso-" + UUID.randomUUID() + "@example.invalid");
        user.setEnabled(true);
        user.setOidcProvider(OidcClientConfig.REGISTRATION_ID);
        user.setOidcSubject("sub-" + UUID.randomUUID());
        user.addRole(service.listRoles().stream()
                .filter(role -> role.getName().equals("GUEST")).findFirst().orElseThrow());
        me = users.save(user);
    }

    private RequestPostProcessor asSso() {
        Instant now = Instant.now();
        OidcIdToken token = OidcIdToken.withTokenValue("id-token").subject(me.getOidcSubject())
                .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        var details = new DarkholdUserDetails(me, token, new OidcUserInfo(Map.of("sub", me.getOidcSubject())));
        return authentication(new OAuth2AuthenticationToken(details, details.getAuthorities(),
                OidcClientConfig.REGISTRATION_ID));
    }

    private void saveProfile(final String firstName, final String email, final String flash, final String key)
            throws Exception {
        var none = new MockMultipartFile("image", "", "application/octet-stream", new byte[0]);
        mvc.perform(multipart("/account/profile").file(none).with(asSso()).with(csrf())
                        .param("firstName", firstName).param("lastName", "Person").param("email", email))
                .andExpect(redirectedUrl("/account"))
                .andExpect(flash().attribute(flash, key));
    }

    @Test
    void authorizationRequestRedirectsToTheProviderWithOurCallback() throws Exception {
        String location = mvc.perform(get("/oauth2/authorization/oidc"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getRedirectedUrl();
        String decoded = URLDecoder.decode(location, StandardCharsets.UTF_8);

        assertTrue(decoded.startsWith(AUTHORIZE + "?"), decoded);
        assertTrue(decoded.contains("client_id=" + CLIENT_ID), decoded);
        assertTrue(decoded.contains("redirect_uri=http://localhost/login/oauth2/code/oidc"), decoded);
        assertTrue(decoded.contains("state=") && decoded.contains("nonce="), decoded);
    }

    @Test
    void ssoUserReachesProtectedPages() throws Exception {
        mvc.perform(get("/account").with(asSso())).andExpect(status().isOk());
        mvc.perform(get("/viewchallenges").with(asSso())).andExpect(status().isOk());
    }

    /** The login the session store holds under this user's id index; the stored row is removed afterwards. */
    private OAuth2AuthenticationToken storedLoginOfMe() {
        var stored = sessions.findByPrincipalName(String.valueOf(me.getId())).values();
        assertEquals(1, stored.size());
        var session = stored.iterator().next();
        SecurityContext context = session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        sessions.deleteById(session.getId());
        return assertInstanceOf(OAuth2AuthenticationToken.class, context.getAuthentication());
    }

    @Test
    void profileUpdateKeepsTheSsoLoginInTheStoredSessionIndexedByUserId() throws Exception {
        // Prior test contexts recreate member (starting at id 13) while H2's SPRING_SESSION table persists.
        sessions.findByPrincipalName(String.valueOf(me.getId())).keySet().forEach(sessions::deleteById);
        saveProfile("Renamed", me.getEmail(), "message", "account.profileUpdated");

        assertEquals("Renamed", users.findById(me.getId()).orElseThrow().getFirstName());
        OAuth2AuthenticationToken login = storedLoginOfMe();
        var principal = assertInstanceOf(DarkholdUserDetails.class, login.getPrincipal());
        assertEquals(OidcClientConfig.REGISTRATION_ID, login.getAuthorizedClientRegistrationId());
        assertEquals("Renamed#" + me.getId(), principal.getName());
        assertEquals("id-token", principal.getIdToken().getTokenValue());
        assertEquals(me.getOidcSubject(), principal.getUserInfo().getSubject());
    }

    @Test
    void ssoUserCannotChangeTheEmailTheProviderOwns() throws Exception {
        String email = me.getEmail();

        saveProfile("Sso", "other-" + UUID.randomUUID() + "@example.invalid", "errorMessage",
                "account.error.oidcEmail");

        assertEquals(email, users.findById(me.getId()).orElseThrow().getEmail());
    }

    @Test
    void ssoUserCannotSetAPassword() throws Exception {
        mvc.perform(post("/account/password").with(asSso()).with(csrf()).param("currentPassword", "")
                        .param("newPassword", "New-password-456!").param("confirmPassword", "New-password-456!"))
                .andExpect(redirectedUrl("/account"))
                .andExpect(flash().attribute("errorMessage", "account.error.oidcPassword"));

        assertNull(users.findById(me.getId()).orElseThrow().getPassword());
    }

    @Test
    void passwordlessUserCannotUseTheLocalLoginForm() throws Exception {
        mvc.perform(formLogin().user(me.getEmail()).password("")).andExpect(redirectedUrl("/login?error"));

        // Control: with a local password the same account signs in, so the rejection was no lookup miss.
        me.setPassword(encoder.encode("Local-password-123!"));
        users.save(me);
        mvc.perform(formLogin().user(me.getEmail()).password("Local-password-123!")).andExpect(redirectedUrl("/"));
    }

    @Test
    void loginPageShowsSsoButtonAndRendersErrorVariantsAnonymously() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/oidc")));
        for (String error : new String[]{"", "oidc", "disabled", "oidc_conflict"}) {
            mvc.perform(get("/login").queryParam("error", error))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("/oauth2/authorization/oidc")))
                    .andExpect(content().string(containsString("login-error")));
        }
    }

    @Test
    void adminEditKeepsTheSsoLinkAndTheMissingPassword() {
        final String subject = me.getOidcSubject();
        User form = new User();
        form.setId(me.getId());
        form.setFirstName("Edited");
        form.setLastName("ByAdmin");
        form.setEmail(me.getEmail());
        form.setEnabled(true);
        form.setPassword("");
        form.setRoles(me.getRoles());

        User saved = service.save(form);

        assertEquals(OidcClientConfig.REGISTRATION_ID, saved.getOidcProvider());
        assertEquals(subject, saved.getOidcSubject());
        assertNull(saved.getPassword());
    }
}
