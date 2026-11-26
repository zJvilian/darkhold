/**
 * Theme Manager - Kahvoid Quiz Application
 * Milestone 10: UI/UX Polish
 *
 * Manages theme switching between light and dark modes.
 * Persists user preference in localStorage.
 */

const ThemeManager = (function() {
    'use strict';

    // Configuration
    const STORAGE_KEY = 'kahvoid_theme';
    const COLORBLIND_KEY = 'kahvoid_colorblind';
    const COLORBLIND_MODES = ['none', 'deuteranopia', 'protanopia', 'tritanopia'];
    const THEMES = {
        LIGHT: 'light',
        DARK: 'dark'
    };
    const DEFAULT_THEME = THEMES.LIGHT;

    // Current theme state
    let currentTheme = DEFAULT_THEME;
    let systemPreferenceWatched = false;

    /**
     * Initialize the theme system
     * - Loads saved theme from localStorage
     * - Detects system preference if no saved theme (without persisting it)
     * - Restores saved colorblind preference
     * - Watches system preference changes
     */
    function init() {
        const savedTheme = getSavedTheme();
        currentTheme = savedTheme || getSystemPreference();

        applyTheme(currentTheme, false);
        applyColorblindMode(getColorblindMode(), false);
        watchSystemPreference();

        console.log(`[ThemeManager] Initialized with theme: ${currentTheme}`);
    }

    function getColorblindMode() {
        try {
            const saved = localStorage.getItem(COLORBLIND_KEY);
            return COLORBLIND_MODES.includes(saved) ? saved : 'none';
        } catch (error) {
            return 'none';
        }
    }

    function applyColorblindMode(mode, persist = true) {
        const normalized = COLORBLIND_MODES.includes(mode) ? mode : 'none';
        const html = document.documentElement;
        if (normalized === 'none') {
            html.removeAttribute('data-colorblind');
        } else {
            html.setAttribute('data-colorblind', normalized);
        }
        if (persist) {
            try {
                localStorage.setItem(COLORBLIND_KEY, normalized);
            } catch (error) {
                console.warn('[ThemeManager] Failed to save colorblind mode:', error);
            }
        }
        return normalized;
    }

    function setColorblindMode(mode) {
        return applyColorblindMode(mode, true);
    }

    /**
     * Get saved theme from localStorage
     * @returns {string|null} The saved theme or null
     */
    function getSavedTheme() {
        try {
            return localStorage.getItem(STORAGE_KEY);
        } catch (error) {
            console.warn('[ThemeManager] localStorage not available:', error);
            return null;
        }
    }

    /**
     * Save theme to localStorage
     * @param {string} theme - The theme to save
     */
    function saveTheme(theme) {
        try {
            localStorage.setItem(STORAGE_KEY, theme);
        } catch (error) {
            console.warn('[ThemeManager] Failed to save theme:', error);
        }
    }

    /**
     * Detect system color scheme preference
     * @returns {string} THEMES.LIGHT or THEMES.DARK
     */
    function getSystemPreference() {
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            return THEMES.DARK;
        }
        return THEMES.LIGHT;
    }

    /**
     * Apply theme to the document
     * @param {string} theme - The theme to apply
     * @param {boolean} persist - Whether to save explicit choice to localStorage
     */
    function applyTheme(theme, persist = true) {
        const html = document.documentElement;

        // Set data-theme attribute on html element
        html.setAttribute('data-theme', theme);

        // Update current theme
        currentTheme = theme;

        // Save to localStorage only on explicit user action
        if (persist) {
            saveTheme(theme);
        }

        // Dispatch custom event for other components to react
        window.dispatchEvent(new CustomEvent('themechange', {
            detail: { theme: theme }
        }));

        console.log(`[ThemeManager] Applied theme: ${theme}`);
    }

    /**
     * Toggle between light and dark themes
     * @returns {string} The new theme
     */
    function toggle() {
        const newTheme = currentTheme === THEMES.LIGHT ? THEMES.DARK : THEMES.LIGHT;
        applyTheme(newTheme, true);
        return newTheme;
    }

    /**
     * Set a specific theme
     * @param {string} theme - The theme to set (light or dark)
     * @returns {boolean} True if successful
     */
    function setTheme(theme) {
        if (theme !== THEMES.LIGHT && theme !== THEMES.DARK) {
            console.error(`[ThemeManager] Invalid theme: ${theme}`);
            return false;
        }
        applyTheme(theme, true);
        return true;
    }

    /**
     * Get the current theme
     * @returns {string} Current theme
     */
    function getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Check if current theme is dark
     * @returns {boolean} True if dark theme is active
     */
    function isDark() {
        return currentTheme === THEMES.DARK;
    }

    /**
     * Check if current theme is light
     * @returns {boolean} True if light theme is active
     */
    function isLight() {
        return currentTheme === THEMES.LIGHT;
    }

    /**
     * Listen to system theme changes (optional enhancement)
     */
    function watchSystemPreference() {
        if (systemPreferenceWatched || !window.matchMedia) {
            return;
        }
        systemPreferenceWatched = true;
        const darkModeQuery = window.matchMedia('(prefers-color-scheme: dark)');
        const onChange = function(e) {
            if (!getSavedTheme()) {
                applyTheme(e.matches ? THEMES.DARK : THEMES.LIGHT, false);
            }
        };
        if (darkModeQuery.addEventListener) {
            darkModeQuery.addEventListener('change', onChange);
        } else if (darkModeQuery.addListener) {
            darkModeQuery.addListener(onChange);
        }
    }

    // Public API
    return {
        init: init,
        toggle: toggle,
        setTheme: setTheme,
        getCurrentTheme: getCurrentTheme,
        isDark: isDark,
        isLight: isLight,
        setColorblindMode: setColorblindMode,
        getColorblindMode: getColorblindMode,
        THEMES: THEMES
    };
})();

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() {
        ThemeManager.init();
    });
} else {
    // DOM already loaded
    ThemeManager.init();
}
