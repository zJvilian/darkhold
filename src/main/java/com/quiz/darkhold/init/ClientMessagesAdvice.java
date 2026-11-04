package com.quiz.darkhold.init;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@ControllerAdvice
public class ClientMessagesAdvice {
    @ModelAttribute("clientMessages")
    public Map<String, String> messages(final Locale locale, final HttpServletRequest request) {
        // Set the CSRF cookie before the translated inline messages can flush the response.
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrf != null) {
            csrf.getToken();
        }
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
        return bundle.keySet().stream().filter(key -> key.startsWith("live.") || key.startsWith("client."))
                .collect(Collectors.toMap(key -> key, bundle::getString));
    }
}
