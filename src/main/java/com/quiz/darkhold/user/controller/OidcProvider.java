package com.quiz.darkhold.user.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.ArrayList;
import java.util.List;

/** A single sign-on button on the login and registration pages. */
public record OidcProvider(String id, String name) {

    static List<OidcProvider> from(final ObjectProvider<ClientRegistrationRepository> repositories) {
        List<OidcProvider> providers = new ArrayList<>();
        ClientRegistrationRepository repository = repositories == null ? null : repositories.getIfAvailable();
        if (repository instanceof Iterable<?> registrations) {
            for (Object entry : registrations) {
                if (entry instanceof ClientRegistration client) {
                    providers.add(new OidcProvider(client.getRegistrationId(), client.getClientName()));
                }
            }
        }
        return providers;
    }
}
