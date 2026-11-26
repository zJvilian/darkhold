package com.quiz.darkhold.user.service;

import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.repository.RoleRepository;
import com.quiz.darkhold.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.support.TransactionOperations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DarkholdOidcUserServiceTest {
    private static final String PROVIDER = "oidc";
    private static final String SUBJECT = "sub-1";
    private static final String EMAIL = "ada@example.org";

    private UserRepository users;
    private RoleRepository roles;
    private DarkholdOidcUserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        service = new DarkholdOidcUserService(users, roles, TransactionOperations.withoutTransaction());
        when(users.findByOidcProviderAndOidcSubject(PROVIDER, SUBJECT)).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(42L);
            }
            return saved;
        });
        when(roles.findAll()).thenReturn(List.of(new Role(1L, "ADMIN"), new Role(2L, "GUEST")));
    }

    /** An IdP login whose id token carries the given claims, the email first unless overridden. */
    private static OidcUser oidcUser(final Map<String, Object> claims) {
        Map<String, Object> all = new HashMap<>();
        all.put("email", EMAIL);
        all.putAll(claims);
        all.values().removeIf(value -> value == null);
        Instant now = Instant.now();
        OidcIdToken token = OidcIdToken.withTokenValue("id-token").subject(SUBJECT)
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claims(c -> c.putAll(all)).build();
        return new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), token,
                new OidcUserInfo(Map.of("sub", SUBJECT, "locale", "de")));
    }

    private static User account(final Long id, final String firstName, final String password) {
        User user = new User();
        user.setId(id);
        user.setFirstName(firstName);
        user.setLastName("Lovelace");
        user.setEmail(EMAIL);
        user.setPassword(password);
        user.setEnabled(true);
        user.addRole(new Role(2L, "GUEST"));
        return user;
    }

    private void assertRejected(final String code, final OidcUser login) {
        var error = assertThrows(OAuth2AuthenticationException.class,
                () -> service.resolveOrProvisionUser(PROVIDER, login));
        assertEquals(code, error.getError().getErrorCode());
    }

    @Test
    void returningUserIsFoundByLinkedIdentityAndCarriesTheTokens() {
        User linked = account(7L, "Ada", null);
        linked.setOidcProvider(PROVIDER);
        linked.setOidcSubject(SUBJECT);
        when(users.findByOidcProviderAndOidcSubject(PROVIDER, SUBJECT)).thenReturn(Optional.of(linked));

        DarkholdUserDetails details = service.resolveOrProvisionUser(PROVIDER,
                oidcUser(Map.of("email", "renamed-at-idp@example.org")));

        assertEquals("Ada#7", details.getName());
        assertEquals(Set.of("GUEST"), AuthorityUtils.authorityListToSet(details.getAuthorities()));
        assertEquals("id-token", details.getIdToken().getTokenValue());
        assertEquals("de", details.getUserInfo().getLocale());
        verify(users, never()).save(any());
    }

    @Test
    void newUserIsProvisionedAsEnabledPasswordlessGuest() {
        when(users.findByEmail(EMAIL)).thenReturn(null);

        DarkholdUserDetails details = service.resolveOrProvisionUser(PROVIDER,
                oidcUser(Map.of("given_name", "Ada", "family_name", "Lovelace")));

        User saved = details.getUser();
        assertEquals("Ada#42", details.getName());
        assertEquals("Lovelace", saved.getLastName());
        assertEquals(EMAIL, saved.getEmail());
        assertTrue(saved.getEnabled());
        assertNull(saved.getPassword());
        assertFalse(saved.hasLocalPassword());
        assertEquals(PROVIDER, saved.getOidcProvider());
        assertEquals(SUBJECT, saved.getOidcSubject());
        assertEquals(Set.of("GUEST"), saved.getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
    }

    static Stream<Arguments> nameClaims() {
        String longName = "x".repeat(70);
        return Stream.of(
                Arguments.of(Map.of("name", "Ada  King Lovelace"), "Ada King", "Lovelace"),
                Arguments.of(Map.of("name", "Ada"), "Ada", "-"),
                Arguments.of(Map.of("given_name", " ", "name", " "), "ada", "-"),
                Arguments.of(Map.of("given_name", longName, "family_name", longName), "x".repeat(64), "x".repeat(64)));
    }

    @ParameterizedTest
    @MethodSource("nameClaims")
    void provisionedNamesFallBackAndAreClamped(final Map<String, Object> claims, final String first,
                                               final String last) {
        DarkholdUserDetails details = service.resolveOrProvisionUser(PROVIDER, oidcUser(claims));

        assertEquals(first, details.getUser().getFirstName());
        assertEquals(last, details.getUser().getLastName());
    }

    @Test
    void verifiedEmailLinksTheExistingLocalAccount() {
        User local = account(3L, "Local", "bcrypt-hash");
        when(users.findByEmail(EMAIL)).thenReturn(local);

        DarkholdUserDetails details = service.resolveOrProvisionUser(PROVIDER,
                oidcUser(Map.of("email_verified", true)));

        assertEquals("Local#3", details.getName());
        assertEquals(PROVIDER, local.getOidcProvider());
        assertEquals(SUBJECT, local.getOidcSubject());
        assertEquals("bcrypt-hash", local.getPassword());
        verify(users).save(local);
    }

    @Test
    void unverifiedOrUnstatedEmailNeverTakesOverAnExistingAccount() {
        User local = account(3L, "Local", "bcrypt-hash");
        when(users.findByEmail(EMAIL)).thenReturn(local);

        assertRejected(DarkholdOidcUserService.EMAIL_CONFLICT, oidcUser(Map.of("email_verified", false)));
        assertRejected(DarkholdOidcUserService.EMAIL_CONFLICT, oidcUser(Map.of()));

        assertNull(local.getOidcSubject());
        verify(users, never()).save(any());
    }

    @Test
    void emailLinkedToAnotherIdentityIsRejected() {
        User other = account(3L, "Other", null);
        other.setOidcProvider(PROVIDER);
        other.setOidcSubject("someone-else");
        when(users.findByEmail(EMAIL)).thenReturn(other);

        assertRejected(DarkholdOidcUserService.EMAIL_CONFLICT, oidcUser(Map.of("email_verified", true)));

        assertEquals("someone-else", other.getOidcSubject());
        verify(users, never()).save(any());
    }

    @Test
    void disabledAccountIsRejected() {
        User disabled = account(7L, "Ada", null);
        disabled.setEnabled(false);
        when(users.findByOidcProviderAndOidcSubject(PROVIDER, SUBJECT)).thenReturn(Optional.of(disabled));

        assertRejected(DarkholdOidcUserService.ACCOUNT_DISABLED, oidcUser(Map.of()));
    }

    @Test
    void unusableEmailIsRejectedBeforeAnyLookup() {
        for (String email : Arrays.asList(null, "", "   ", "no-at-sign", "@example.org",
                "a".repeat(120) + "@example.org")) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("email", email);
            assertRejected(DarkholdOidcUserService.MISSING_EMAIL, oidcUser(claims));
        }
        verifyNoInteractions(users, roles);
    }

    @Test
    void concurrentFirstLoginReusesTheIdentityTheOtherRequestCreated() {
        User winner = account(9L, "Ada", null);
        when(users.findByOidcProviderAndOidcSubject(PROVIDER, SUBJECT))
                .thenReturn(Optional.empty(), Optional.of(winner));
        doThrow(new DataIntegrityViolationException("duplicate")).when(users).save(any(User.class));

        assertEquals("Ada#9", service.resolveOrProvisionUser(PROVIDER, oidcUser(Map.of())).getName());
    }

    @Test
    void concurrentRegistrationOfTheSameEmailIsAConflict() {
        doThrow(new DataIntegrityViolationException("duplicate")).when(users).save(any(User.class));

        assertRejected(DarkholdOidcUserService.EMAIL_CONFLICT, oidcUser(Map.of()));
    }

    private static Object roundTrip(final Object value) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }

    @Test
    void oidcLoginSurvivesJavaSerializationLikeTheJdbcSessionStore() throws Exception {
        OidcUser login = oidcUser(Map.of("email_verified", true));
        var details = new DarkholdUserDetails(account(7L, "Ada", null), login.getIdToken(), login.getUserInfo());
        var token = new OAuth2AuthenticationToken(details, details.getAuthorities(), PROVIDER);

        var copy = assertInstanceOf(OAuth2AuthenticationToken.class, roundTrip(token));
        var principal = assertInstanceOf(DarkholdUserDetails.class, copy.getPrincipal());
        assertEquals(PROVIDER, copy.getAuthorizedClientRegistrationId());
        assertEquals("Ada#7", principal.getName());
        assertEquals("id-token", principal.getIdToken().getTokenValue());
        assertEquals(SUBJECT, principal.getSubject());
        assertEquals("de", principal.getUserInfo().getLocale());
        assertEquals(Set.of("GUEST"), AuthorityUtils.authorityListToSet(copy.getAuthorities()));
    }
}
