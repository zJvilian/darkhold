package com.quiz.darkhold.user.entity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The signed-in account, whether it logged in with a password or through OpenID Connect.
 * Both ways yield this principal, so everything keyed by {@link #getUsername()} works the same.
 */
public class DarkholdUserDetails implements UserDetails, OidcUser {
    private static final long serialVersionUID = 1L;

    private final User user;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public DarkholdUserDetails(final User user) {
        this(user, null, null);
    }

    public DarkholdUserDetails(final User user, final OidcIdToken idToken, final OidcUserInfo userInfo) {
        this.user = user;
        this.idToken = idToken;
        this.userInfo = userInfo;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles()
                .stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .toList();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /**
     * The name games, results and host checks are keyed by. First names are not unique, so the id is part of it;
     * "#" can never appear in a guest nickname, so a guest cannot take a registered user's identity.
     * shortcut: renaming an account changes this, so its older games drop out of its lists; key by id alone
     * once those lists matter after a rename.
     */
    @Override
    public String getUsername() {
        return user.getFirstName() + "#" + user.getId();
    }

    @Override
    public String getName() {
        return getUsername();
    }

    @Override
    public Map<String, Object> getClaims() {
        Map<String, Object> claims = new HashMap<>();
        if (userInfo != null) {
            claims.putAll(userInfo.getClaims());
        }
        if (idToken != null) {
            claims.putAll(idToken.getClaims());
        }
        return claims;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public String getEmail() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(user.getEnabled());
    }

    public User getUser() {
        return this.user;
    }
}
