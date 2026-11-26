package com.quiz.darkhold.user.service;

import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.repository.RoleRepository;
import com.quiz.darkhold.user.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.HashSet;
import java.util.Set;

/**
 * Maps an OpenID Connect login to a Darkhold account: by linked identity first, then by verified email,
 * else a new GUEST account. The database work runs after the provider calls, in its own short transaction.
 */
@Service
public class DarkholdOidcUserService extends OidcUserService {
    public static final String MISSING_EMAIL = "missing_email";
    public static final String EMAIL_CONFLICT = "email_conflict";
    public static final String ACCOUNT_DISABLED = "account_disabled";

    private static final int MAX_NAME = 64;
    private static final int MAX_EMAIL = 128;

    private final Logger logger = LogManager.getLogger(DarkholdOidcUserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TransactionOperations transactions;

    public DarkholdOidcUserService(final UserRepository userRepository, final RoleRepository roleRepository,
                                   final TransactionOperations transactions) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.transactions = transactions;
    }

    @Override
    public OidcUser loadUser(final OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);
        return resolveOrProvisionUser(userRequest.getClientRegistration().getRegistrationId(), oidcUser);
    }

    DarkholdUserDetails resolveOrProvisionUser(final String provider, final OidcUser oidcUser) {
        String email = requireEmail(oidcUser);
        boolean emailVerified = Boolean.TRUE.equals(oidcUser.getEmailVerified());
        User user;
        try {
            user = transactions.execute(status -> findOrCreate(provider, email, emailVerified, oidcUser));
        } catch (DataIntegrityViolationException exception) {
            // A concurrent first login created the identity, or someone registered the email meanwhile.
            user = userRepository.findByOidcProviderAndOidcSubject(provider, oidcUser.getSubject())
                    .orElseThrow(() -> failure(EMAIL_CONFLICT, "The email is already used by another account"));
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw failure(ACCOUNT_DISABLED, "Account is disabled");
        }
        return new DarkholdUserDetails(user, oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    private static String requireEmail(final OidcUser oidcUser) {
        String email = oidcUser.getEmail() == null ? "" : oidcUser.getEmail().trim();
        if (email.isEmpty() || email.length() > MAX_EMAIL || email.indexOf('@') < 1) {
            throw failure(MISSING_EMAIL, "The identity provider did not supply a usable email address");
        }
        return email;
    }

    private User findOrCreate(final String provider, final String email, final boolean emailVerified,
                              final OidcUser oidcUser) {
        var linked = userRepository.findByOidcProviderAndOidcSubject(provider, oidcUser.getSubject());
        if (linked.isPresent()) {
            return linked.get();
        }
        User existing = userRepository.findByEmail(email);
        return existing == null
                ? provision(provider, email, oidcUser)
                : link(existing, provider, oidcUser.getSubject(), emailVerified);
    }

    private User link(final User existing, final String provider, final String subject,
                      final boolean emailVerified) {
        // Linking an unverified email, or over another identity, would let the IdP account take this one over.
        if (!emailVerified || existing.getOidcSubject() != null) {
            throw failure(EMAIL_CONFLICT, "The email is already used by another account");
        }
        existing.setOidcProvider(provider);
        existing.setOidcSubject(subject);
        logger.info("Linked OIDC identity to user {}", existing.getId());
        return userRepository.save(existing);
    }

    private User provision(final String provider, final String email, final OidcUser oidcUser) {
        User user = new User();
        String[] names = names(oidcUser, email);
        user.setFirstName(names[0]);
        user.setLastName(names[1]);
        user.setEmail(email);
        user.setEnabled(true);
        user.setOidcProvider(provider);
        user.setOidcSubject(oidcUser.getSubject());
        user.setRoles(guestRoles());
        User saved = userRepository.save(user);
        logger.info("Provisioned OIDC user {}", saved.getId());
        return saved;
    }

    /** The same default role a self-registered account gets. */
    private Set<Role> guestRoles() {
        Set<Role> roles = new HashSet<>();
        roleRepository.findAll().forEach(role -> {
            if ("GUEST".equals(role.getName())) {
                roles.add(role);
            }
        });
        return roles;
    }

    /** First and last name from the profile claims, else the email's local part; never empty, at most 64 chars. */
    private static String[] names(final OidcUser oidcUser, final String email) {
        String first = clean(oidcUser.getGivenName());
        String last = clean(oidcUser.getFamilyName());
        if (first == null) {
            String[] split = splitFullName(oidcUser.getFullName());
            first = split[0];
            last = last == null ? split[1] : last;
        }
        if (first == null) {
            first = clean(email.substring(0, email.indexOf('@')));
        }
        return new String[]{first, last == null ? "-" : last};
    }

    /** "Ada King Lovelace" becomes {"Ada King", "Lovelace"}; a single word has no last name. */
    private static String[] splitFullName(final String fullName) {
        String full = clean(fullName);
        if (full == null) {
            return new String[]{null, null};
        }
        String normalized = full.replaceAll("\\s+", " ");
        int space = normalized.lastIndexOf(' ');
        return space > 0
                ? new String[]{clean(normalized.substring(0, space)), clean(normalized.substring(space + 1))}
                : new String[]{normalized, null};
    }

    private static String clean(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME).trim() : trimmed;
    }

    private static OAuth2AuthenticationException failure(final String code, final String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), message);
    }
}
