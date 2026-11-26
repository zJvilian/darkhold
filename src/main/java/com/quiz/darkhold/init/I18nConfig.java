package com.quiz.darkhold.init;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.util.Locale;

/**
 * Internationalization (i18n) configuration for the Kahvoid application.
 * Configures message sources, locale resolution, and locale change interceptor.
 */
@Configuration
public class I18nConfig {
    private final boolean secureCookie;

    public I18nConfig() {
        this("");
    }

    @Autowired
    public I18nConfig(@Value("${darkhold.public-base-url:}") final String publicBaseUrl) {
        this.secureCookie = publicBaseUrl != null && publicBaseUrl.regionMatches(true, 0, "https://", 0, 8);
    }

    /**
     * Configures the MessageSource for loading internationalized messages.
     * Uses ReloadableResourceBundleMessageSource for development flexibility.
     *
     * @return configured MessageSource bean
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames("classpath:messages", "classpath:validation");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600); // Cache for 1 hour in production
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }

    /**
     * Configures the LocaleResolver to determine the current locale.
     * Keeps the chosen language across navigation, login and new browser sessions.
     *
     * @return configured LocaleResolver bean
     */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("kahvoid_language");
        resolver.setCookieMaxAge(java.time.Duration.ofDays(365));
        resolver.setCookieSameSite("Lax");
        resolver.setCookieHttpOnly(true);
        resolver.setCookieSecure(secureCookie);
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    /**
     * Configures the LocaleChangeInterceptor to allow changing locale via URL parameter.
     * Users can switch language by adding ?lang=de (or another locale code) to any URL.
     *
     * @return configured LocaleChangeInterceptor bean
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }
}
