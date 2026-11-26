package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.user.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@DisplayName("LoginController Tests")
class LoginControllerTest {

    @Mock
    private SecurityService securityService;

    @Mock
    private Model model;

    @Mock
    private ObjectProvider<ClientRegistrationRepository> registrations;

    private LoginController loginController;

    @BeforeEach
    void setUp() {
        loginController = new LoginController(securityService);
    }

    @Nested
    @DisplayName("login() method tests")
    class LoginMethodTests {

        @Test
        @DisplayName("should redirect to home when user is already authenticated")
        void testLogin_WhenAuthenticated_ShouldRedirectToHome() {
            // Given
            when(securityService.isAuthenticated()).thenReturn(true);

            // When
            var result = loginController.login(model, null, null);

            // Then
            assertEquals("redirect:/", result);
            verify(model, never()).addAttribute(anyString(), any());
        }

        @Test
        @DisplayName("should display error message when error parameter is present")
        void testLogin_WhenErrorPresent_ShouldDisplayErrorMessage() {
            // Given
            when(securityService.isAuthenticated()).thenReturn(false);
            var error = "error";

            // When
            var result = loginController.login(model, error, null);

            // Then
            assertEquals("login", result);
            verify(model).addAttribute("danger", "Your username or password is invalid.");
        }

        @Test
        @DisplayName("should display logout message when logout parameter is present")
        void testLogin_WhenLogoutPresent_ShouldDisplayLogoutMessage() {
            // Given
            when(securityService.isAuthenticated()).thenReturn(false);
            var logout = "true";

            // When
            var result = loginController.login(model, null, logout);

            // Then
            assertEquals("login", result);
            verify(model).addAttribute("message", "You have been logged out successfully.");
        }

        @Test
        @DisplayName("should not add any attributes when no error or logout")
        void testLogin_WhenNoErrorOrLogout_ShouldReturnLoginView() {
            // Given
            when(securityService.isAuthenticated()).thenReturn(false);

            // When
            var result = loginController.login(model, null, null);

            // Then
            assertEquals("login", result);
            verify(model, never()).addAttribute(anyString(), any());
        }

        @Test
        @DisplayName("should handle both error and logout when both are present")
        void testLogin_WhenBothErrorAndLogout_ShouldPrioritizeLogout() {
            // Given
            when(securityService.isAuthenticated()).thenReturn(false);
            var error = "error";
            var logout = "true";

            // When
            var result = loginController.login(model, error, logout);

            // Then
            assertEquals("login", result);
            // When both are present, logout takes priority in modelAttributes logic
            verify(model).addAttribute("message", "You have been logged out successfully.");
        }

        @Test
        @DisplayName("should set locked flag when error is locked")
        void testLogin_WhenErrorLocked_ShouldSetLockedFlag() {
            when(securityService.isAuthenticated()).thenReturn(false);

            var result = loginController.login(model, "locked", null);

            assertEquals("login", result);
            verify(model).addAttribute("locked", true);
            verify(model).addAttribute(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("single sign-on tests")
    class SingleSignOnTests {

        @ParameterizedTest
        @CsvSource({
            "disabled, login.error.disabled",
            "oidc_conflict, login.error.oidcConflict",
            "oidc, login.error.oidc"
        })
        @DisplayName("should map OIDC errors to a message key")
        void testLogin_WhenOidcError_ShouldSetErrorKey(final String error, final String errorKey) {
            when(securityService.isAuthenticated()).thenReturn(false);

            var result = loginController.login(model, error, null);

            assertEquals("login", result);
            verify(model).addAttribute("errorKey", errorKey);
            verify(model).addAttribute("danger", "Single sign-on failed.");
        }

        @Test
        @DisplayName("should offer no providers without a client registration repository")
        void testOidcProviders_WhenNotConfigured_ShouldBeEmpty() {
            assertEquals(List.of(), loginController.oidcProviders());
        }

        @Test
        @DisplayName("should offer every configured provider")
        void testOidcProviders_WhenConfigured_ShouldListThem() {
            var client = ClientRegistration.withRegistrationId("oidc")
                    .clientId("darkhold")
                    .clientName("Company SSO")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri("https://id.example.invalid/authorize")
                    .tokenUri("https://id.example.invalid/token")
                    .build();
            when(registrations.getIfAvailable()).thenReturn(new InMemoryClientRegistrationRepository(client));

            var controller = new LoginController(securityService, registrations);

            assertEquals(List.of(new OidcProvider("oidc", "Company SSO")), controller.oidcProviders());
        }
    }

    @Nested
    @DisplayName("logout() method tests")
    class LogoutMethodTests {

        @Test
        @DisplayName("GET /logout should redirect to /login without CSRF-less logout")
        void testLogout_ShouldRedirectToLogin() {
            String result = loginController.logout();
            assertEquals("redirect:/login", result);
        }
    }

    @Nested
    @DisplayName("loginMe() method tests")
    class LoginMeMethodTests {

        @Test
        @DisplayName("should return login view name for POST request")
        void testLoginMe_ShouldReturnLoginViewName() {
            // When
            var result = loginController.loginMe();

            // Then
            assertEquals("login", result);
        }
    }

    @Nested
    @DisplayName("loginMe2() method tests")
    class LoginMe2MethodTests {

        @Test
        @DisplayName("should return login view name for GET request")
        void testLoginMe2_ShouldReturnLoginViewName() {
            // When
            var result = loginController.loginMe2();

            // Then
            assertEquals("login", result);
        }
    }

    @Nested
    @DisplayName("Integration scenario tests")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("should handle complete login flow: login -> redirect when authenticated")
        void testCompleteLoginFlow_WhenAlreadyAuthenticated() {
            // Given
            when(securityService.isAuthenticated()).thenReturn(true);

            // When
            var result = loginController.login(model, null, null);

            // Then
            assertEquals("redirect:/", result);
            verify(securityService).isAuthenticated();
        }

        @Test
        @DisplayName("should handle login page with error and logout parameters")
        void testLoginWithMultipleParameters() {
            // Given
            when(securityService.isAuthenticated()).thenReturn(false);

            // When
            var resultWithError = loginController.login(model, "true", null);
            var resultWithLogout = loginController.login(model, null, "true");

            // Then
            assertEquals("login", resultWithError);
            assertEquals("login", resultWithLogout);
            verify(model).addAttribute("danger", "Your username or password is invalid.");
            verify(model).addAttribute("message", "You have been logged out successfully.");
        }
    }
}

