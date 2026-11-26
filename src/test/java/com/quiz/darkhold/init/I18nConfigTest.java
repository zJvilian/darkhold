package com.quiz.darkhold.init;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("I18nConfig Tests")
class I18nConfigTest {

    private I18nConfig config;

    @BeforeEach
    void setUp() {
        config = new I18nConfig();
    }

    @Test
    @DisplayName("Should return ReloadableResourceBundleMessageSource")
    void shouldReturnReloadableMessageSource() {
        var messageSource = config.messageSource();
        assertInstanceOf(ReloadableResourceBundleMessageSource.class, messageSource);
    }

    @Test
    @DisplayName("Should return non-null message source")
    void shouldReturnNonNullMessageSource() {
        assertNotNull(config.messageSource());
    }

    @Test
    @DisplayName("Should use code as default message")
    void shouldUseCodeAsDefaultMessage() {
        var messageSource = config.messageSource();
        var resolved = messageSource.getMessage("nonexistent.key", null, Locale.ENGLISH);
        assertEquals("nonexistent.key", resolved);
    }

    @Test
    @DisplayName("Should return CookieLocaleResolver")
    void shouldReturnCookieLocaleResolver() {
        var resolver = config.localeResolver();
        assertInstanceOf(CookieLocaleResolver.class, resolver);
    }

    @Test
    @DisplayName("Should set default locale to English")
    void shouldSetDefaultLocaleToEnglish() {
        var resolver = config.localeResolver();
        var request = new MockHttpServletRequest();
        var locale = resolver.resolveLocale(request);
        assertEquals(Locale.ENGLISH, locale);
    }

    @Test
    @DisplayName("Should return LocaleChangeInterceptor")
    void shouldReturnLocaleChangeInterceptor() {
        var interceptor = config.localeChangeInterceptor();
        assertInstanceOf(LocaleChangeInterceptor.class, interceptor);
    }

    @Test
    @DisplayName("Should set param name to lang")
    void shouldSetParamNameToLang() {
        var interceptor = config.localeChangeInterceptor();
        assertEquals("lang", interceptor.getParamName());
    }

    @Test
    @DisplayName("Should return non-null locale resolver")
    void shouldReturnNonNullLocaleResolver() {
        assertNotNull(config.localeResolver());
    }

    private static String languageCookie(final I18nConfig i18nConfig) {
        var response = new MockHttpServletResponse();
        i18nConfig.localeResolver().setLocale(new MockHttpServletRequest(), response, Locale.GERMAN);
        return response.getHeader("Set-Cookie");
    }

    @Test
    @DisplayName("Language cookie is HttpOnly and only Secure behind an https public URL")
    void languageCookieFlags() {
        String https = languageCookie(new I18nConfig("HTTPS://quiz.example.test"));
        assertTrue(https.startsWith("kahvoid_language=") && https.contains("HttpOnly"));
        assertTrue(https.contains("Secure"));
        assertTrue(languageCookie(config).contains("HttpOnly"));
        assertFalse(languageCookie(config).contains("Secure"));
        assertFalse(languageCookie(new I18nConfig("http://quiz.example.test")).contains("Secure"));
    }
}
