/**
 * CSRF Token Manager for Kahvoid
 * Handles CSRF token extraction and injection for AJAX requests and WebSocket connections
 */

const CsrfManager = (() => {
    let csrfToken = null;
    let csrfHeader = null;

    /**
     * Initialize CSRF manager by reading token from cookie
     */
    function init() {
        // Read CSRF token from cookie (set by CookieCsrfTokenRepository)
        csrfToken = getCookie('XSRF-TOKEN');
        csrfHeader = 'X-XSRF-TOKEN';

        if (!csrfToken) {
            console.warn('CSRF token not found in cookies');
        }
    }

    /**
     * Get cookie value by name
     */
    function getCookie(name) {
        const value = `; ${document.cookie}`;
        const parts = value.split(`; ${name}=`);
        if (parts.length === 2) {
            return parts.pop().split(';').shift();
        }
        return null;
    }

    /**
     * Get CSRF token (always reads fresh cookie to avoid stale cached tokens)
     */
    function getToken() {
        csrfToken = getCookie('XSRF-TOKEN');
        return csrfToken;
    }

    /**
     * Get CSRF header name
     */
    function getHeaderName() {
        return csrfHeader;
    }

    /**
     * Add CSRF token to XMLHttpRequest
     */
    function addTokenToXHR(xhr) {
        const token = getToken();
        if (token) {
            xhr.setRequestHeader(csrfHeader, token);
        }
    }

    /**
     * Add CSRF token to fetch request headers
     */
    function addTokenToFetch(headers = {}) {
        const token = getToken();
        if (token) {
            headers[csrfHeader] = token;
        }
        return headers;
    }

    /**
     * Get CSRF token as form parameter for WebSocket connections
     * STOMP requires token in CONNECT frame
     */
    function getTokenForWebSocket() {
        return {
            parameterName: '_csrf',
            token: getToken()
        };
    }

    /**
     * Get CSRF headers for STOMP connection
     * Returns object with X-XSRF-TOKEN header
     */
    function getHeadersForStomp() {
        const token = getToken();
        return token ? { [csrfHeader]: token } : {};
    }

    /**
     * Create hidden input field with CSRF token for forms
     */
    function createHiddenInput() {
        const token = getToken();
        if (!token) {
            return null;
        }
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = '_csrf';
        input.value = token;
        return input;
    }

    /**
     * Add CSRF token to all forms on page load
     */
    function protectForms() {
        document.querySelectorAll('form').forEach(form => {
            // GET forms need no token, and it would end up in the URL and browser history
            if (form.method.toUpperCase() === 'GET') {
                return;
            }
            // Skip forms that already have CSRF token
            if (form.querySelector('input[name="_csrf"]')) {
                return;
            }

            // Add CSRF token to form
            const input = createHiddenInput();
            if (input) {
                form.appendChild(input);
            }
        });
    }

    // Auto-initialize on script load
    init();

    // Expose public API
    return {
        getToken,
        getHeaderName,
        addTokenToXHR,
        addTokenToFetch,
        getTokenForWebSocket,
        getHeadersForStomp,
        createHiddenInput,
        protectForms,
        refresh: init
    };
})();

// Auto-protect forms when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        CsrfManager.protectForms();
    });
} else {
    CsrfManager.protectForms();
}
