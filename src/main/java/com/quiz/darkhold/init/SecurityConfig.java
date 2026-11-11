package com.quiz.darkhold.init;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {
    private static final String[] GUEST_GAME_PATHS = {"/interstitial", "/question", "/game", "/answer/",
        "/validate_answer/", "/scoreboard", "/final", "/team/list/**", "/darkhold-websocket/**", "/logout"};


    private final UserDetailsService userDetailsService;

    public SecurityConfig(final UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public static BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Index stored sessions by user id (not by the non-unique first name Spring would use). */
    @Bean
    public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionUserIdIndex() {
        return repository -> repository.setIndexResolver(session -> {
            var context = session.<SecurityContext>getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (context != null && context.getAuthentication() != null
                    && context.getAuthentication().getPrincipal() instanceof DarkholdUserDetails details) {
                return Map.of(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                        String.valueOf(details.getUser().getId()));
            }
            return Map.of();
        });
    }

    /** Boot ignores cookie max-age for stored sessions, so without this a login ends when the browser closes. */
    @Bean
    public DefaultCookieSerializerCustomizer sessionCookieLifetime(
            @Value("${spring.session.timeout}") final Duration timeout) {
        return serializer -> serializer.setCookieMaxAge((int) timeout.toSeconds());
    }

    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http, final RateLimitingService rateLimiter)
            throws Exception {
        // Enable CSRF protection with cookie-based token repository
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        // Set the name of the attribute the CsrfToken will be populated on
        requestHandler.setCsrfRequestAttributeName("_csrf");

        http.csrf((csrf) -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
        );

        http.authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll());

        for (String paths : matchingPaths()) {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(paths).permitAll()
            );
        }
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin", "/admin/**", "/userManagement", "/user/**", "/users/**")
                .hasAuthority("ADMIN")
                .requestMatchers("/gameManagement").hasAnyAuthority("ADMIN", "GAME_MANAGER")
                .requestMatchers(GUEST_GAME_PATHS).authenticated()
                // PIN guests are signed in without a role: they may play, everything else needs an account
                .anyRequest().access((authentication, context) -> new AuthorizationDecision(
                        !(authentication.get() instanceof AnonymousAuthenticationToken)
                                && authentication.get().isAuthenticated()
                                && !authentication.get().getAuthorities().isEmpty())));
        configureLogin(http, rateLimiter);
        var login = new LoginUrlAuthenticationEntryPoint("/login");
        http.exceptionHandling(errors -> errors.authenticationEntryPoint((req, res, ex) -> {
            if (ErrorRendering.wantsHtml(req)) {
                login.commence(req, res, ex);
            } else {
                res.sendError(401);
            }
        }));
        http.logout((logout) -> logout.logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN"));

        http.headers(
                (header) -> header.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
        );
        return http.build();
    }

    private void configureLogin(final HttpSecurity http, final RateLimitingService rateLimiter) throws Exception {
        // Throttle password guessing per client IP, in a key space separate from the game PIN limiter
        var loginSuccess = new SavedRequestAwareAuthenticationSuccessHandler();
        loginSuccess.setDefaultTargetUrl("/");
        loginSuccess.setAlwaysUseDefaultTargetUrl(true);
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(final HttpServletRequest req, final HttpServletResponse res,
                                            final FilterChain chain) throws ServletException, IOException {
                if ("POST".equals(req.getMethod()) && (req.getContextPath() + "/login").equals(req.getRequestURI())
                        && !rateLimiter.isAllowed(loginKey(req))) {
                    res.sendRedirect("/login?error");
                    return;
                }
                chain.doFilter(req, res);
            }
        }, UsernamePasswordAuthenticationFilter.class);
        var loginFailure = new SimpleUrlAuthenticationFailureHandler("/login?error");
        http.formLogin((formLogin) -> formLogin.loginPage("/login")
                .failureHandler((req, res, ex) -> {
                    rateLimiter.recordFailedAttempt(loginKey(req));
                    loginFailure.onAuthenticationFailure(req, res, ex);
                })
                .successHandler((req, res, auth) -> {
                    rateLimiter.recordSuccessfulAttempt(loginKey(req));
                    loginSuccess.onAuthenticationSuccess(req, res, auth);
                })
                .permitAll());
    }

    private static String loginKey(final HttpServletRequest request) {
        return "login:" + request.getRemoteAddr();
    }

    private String[] matchingPaths() {
        return new String[]{"/", "/logmein", "/error",
                "/home", "/resources/**", "/actuator/health",
                "/registration", "/images/**", "/question-images/**",
                "/scripts/**", "/styles/**", "/sounds/**",
                "/manifest.json", "/service-worker.js", "/offline.html",
                "/scripts/core/**", "/styles/core/*", "/styles/webfonts/**",
                "/fonts/**", "/favicon.ico",
                "/enterGame", "/joinGame", "/join", "/play/**", "/api/live/**",
                "/impressum", "/datenschutz"
        };
    }

    @Bean
    public AuthenticationManager authManager(final BCryptPasswordEncoder bCryptPasswordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(bCryptPasswordEncoder);
        return new ProviderManager(authProvider);
    }
}
