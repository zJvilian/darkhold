/** Language selection follows the locale used to render the page on the server. */
const LanguageManager = (() => {
    'use strict';
    const SUPPORTED_LANGUAGES = {
        en: { code: 'en', nativeName: 'English' },
        de: { code: 'de', nativeName: 'Deutsch' },
        es: { code: 'es', nativeName: 'Español' },
        fr: { code: 'fr', nativeName: 'Français' }
    };
    let currentLanguage = 'en';

    function init() {
        const rendered = document.documentElement.lang;
        currentLanguage = SUPPORTED_LANGUAGES[rendered] ? rendered : 'en';
        document.querySelectorAll('.language-code, .dh-lang-code').forEach(node => {
            node.textContent = currentLanguage.toUpperCase();
        });
    }

    function buildLanguageUrl(lang) {
        const url = new URL(window.location.href);
        // These old form destinations have equivalent read-only pages.
        const destinations = { '/home': '/', '/viewChallenge': '/challenge/view', '/createChallenge': '/challenge/create' };
        url.pathname = destinations[url.pathname] || url.pathname;
        if (url.pathname === '/preconfigure') {
            url.searchParams.set('challenges', document.getElementById('challengeId').value);
        }
        url.searchParams.set('lang', lang);
        return url.toString();
    }

    function changeLanguage(lang) {
        if (!SUPPORTED_LANGUAGES[lang] || lang === currentLanguage) return;
        window.location.href = buildLanguageUrl(lang);
    }

    function text(key, ...args) {
        const value = window.DarkholdMessages?.[key] ?? key;
        return value.replace(/\{(\d+)\}/g, (match, index) => args[index] ?? match);
    }

    return {
        init, changeLanguage, buildLanguageUrl, text,
        getCurrentLanguage: () => currentLanguage,
        getLanguageInfo: lang => SUPPORTED_LANGUAGES[lang] || null,
        getSupportedLanguages: () => SUPPORTED_LANGUAGES,
        isSupported: lang => !!SUPPORTED_LANGUAGES[lang]
    };
})();

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', LanguageManager.init);
else LanguageManager.init();
