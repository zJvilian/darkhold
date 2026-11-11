package com.quiz.darkhold.user.entity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class DarkholdUserDetails implements UserDetails {
    private static final long serialVersionUID = 1L;

    private final User user;

    public DarkholdUserDetails(final User user) {
        this.user = user;
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
        return user.getEnabled();
    }

    public User getUser() {
        return this.user;
    }
}
