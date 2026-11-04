package com.quiz.darkhold.init;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared error representation for MVC, servlet errors and authentication failures. */
public final class ErrorRendering {
    private ErrorRendering() {
    }

    public static boolean wantsHtml(final HttpServletRequest request) {
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("json")) {
            return false;
        }
        Optional<MediaType> preferred = preferredMediaType(request);
        if (preferred.isPresent()) {
            return preferred.get().isCompatibleWith(MediaType.TEXT_HTML);
        }
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/");
    }

    private static Optional<MediaType> preferredMediaType(final HttpServletRequest request) {
        try {
            return new HeaderContentNegotiationStrategy().resolveMediaTypes(new ServletWebRequest(request)).stream()
                    .filter(type -> type.getQualityValue() > 0 && !type.isWildcardType())
                    .findFirst();
        } catch (org.springframework.web.HttpMediaTypeNotAcceptableException ex) {
            return Optional.of(MediaType.APPLICATION_JSON);
        }
    }

    public static Object response(final HttpServletRequest request, final ResponseEntity<ErrorResponse> error) {
        if (wantsHtml(request)) {
            return html(error.getStatusCode());
        }
        return ResponseEntity.status(error.getStatusCode()).headers(error.getHeaders())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").contentType(MediaType.APPLICATION_JSON)
                .body(error.getBody());
    }

    public static ModelAndView html(final HttpStatusCode status) {
        int messageStatus = Set.of(400, 401, 403, 404, 405, 409, 413, 415, 429, 500, 503).contains(status.value())
                ? status.value() : 500;
        return new ModelAndView("error", Map.of("status", status.value(), "errorMessageStatus", messageStatus), status);
    }
}
