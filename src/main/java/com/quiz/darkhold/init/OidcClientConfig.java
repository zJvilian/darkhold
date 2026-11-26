package com.quiz.darkhold.init;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Single sign-on through one OpenID Connect provider, enabled only when an issuer and client id are set.
 * Without them no registration exists, so startup makes no network call and only password login is offered.
 */
@Configuration
@ConditionalOnExpression("!'${darkhold.oidc.issuer-uri:}'.isBlank() && !'${darkhold.oidc.client-id:}'.isBlank()")
public class OidcClientConfig {
    public static final String REGISTRATION_ID = "oidc";

    @Bean
    @ConditionalOnMissingBean(ClientRegistrationRepository.class)
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${darkhold.oidc.issuer-uri}") final String issuerUri,
            @Value("${darkhold.oidc.client-id}") final String clientId,
            @Value("${darkhold.oidc.client-secret:}") final String clientSecret,
            @Value("${darkhold.oidc.provider-name:SSO}") final String providerName,
            @Value("${darkhold.oidc.scopes:openid,profile,email}") final String[] scopes) {
        ClientRegistration.Builder builder = ClientRegistrations.fromIssuerLocation(issuerUri.trim())
                .registrationId(REGISTRATION_ID)
                .clientId(clientId.trim())
                .clientName(providerName.isBlank() ? "SSO" : providerName.trim())
                .scope(scopes);
        if (clientSecret.isBlank()) {
            // Public client: Spring Security then sends a PKCE challenge instead of a secret.
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        } else {
            builder.clientSecret(clientSecret.trim());
        }
        return new InMemoryClientRegistrationRepository(builder.build());
    }
}
