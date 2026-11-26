/* Click a quiz picture to see it full screen; click next to it (or press Escape) to close it again. */
(function () {
    const SELECTOR = '.question-image, .answer-reveal-image';

    const style = document.createElement('style');
    style.textContent = `
        ${SELECTOR.split(', ').map(s => s + ':not([hidden])').join(', ')} { cursor: zoom-in; }
        .image-zoom-overlay {
            position: fixed; inset: 0; z-index: 10000;
            display: flex; align-items: center; justify-content: center;
            padding: max(12px, env(safe-area-inset-top)) 12px max(12px, env(safe-area-inset-bottom));
            background: rgba(0, 0, 0, 0.88);
            cursor: zoom-out;
            animation: imageZoomIn 0.18s ease-out;
        }
        .image-zoom-overlay img {
            /* as large as the screen allows, small pictures included; the empty band around it closes */
            width: 100%; height: 100%;
            object-fit: contain;
            pointer-events: none;
        }
        @keyframes imageZoomIn { from { opacity: 0; } to { opacity: 1; } }
    `;
    document.head.appendChild(style);

    let overlay = null;
    let opener = null;

    function close() {
        if (!overlay) return;
        overlay.remove();
        overlay = null;
        document.removeEventListener('keydown', onKey);
        if (opener) opener.focus({preventScroll: true});
    }

    function onKey(event) {
        if (event.key === 'Escape') close();
    }

    function open(image) {
        if (overlay || !image.currentSrc && !image.src) return;
        opener = image;
        overlay = document.createElement('div');
        overlay.className = 'image-zoom-overlay';
        overlay.setAttribute('role', 'dialog');
        overlay.setAttribute('aria-modal', 'true');
        overlay.tabIndex = -1;
        const big = document.createElement('img');
        big.src = image.currentSrc || image.src;
        big.alt = image.alt || '';
        overlay.appendChild(big);
        // only a click next to the picture closes it (the img box fills the screen, so find the drawn area)
        overlay.addEventListener('click', event => {
            const box = big.getBoundingClientRect();
            const scale = Math.min(box.width / (big.naturalWidth || 1), box.height / (big.naturalHeight || 1));
            const w = big.naturalWidth * scale, h = big.naturalHeight * scale;
            const left = box.left + (box.width - w) / 2, top = box.top + (box.height - h) / 2;
            const onPicture = event.clientX >= left && event.clientX <= left + w
                && event.clientY >= top && event.clientY <= top + h;
            if (!onPicture) close();
        });
        document.body.appendChild(overlay);
        document.addEventListener('keydown', onKey);
        overlay.focus({preventScroll: true});
    }

    document.addEventListener('click', event => {
        const image = event.target.closest && event.target.closest(SELECTOR);
        if (image && !image.hidden) {
            event.preventDefault();
            open(image);
        }
    });

    // keyboard users: the pictures become focusable and open with Enter/Space
    function makeFocusable(root) {
        root.querySelectorAll(SELECTOR).forEach(image => {
            if (!image.hasAttribute('tabindex')) image.tabIndex = 0;
        });
    }
    document.addEventListener('keydown', event => {
        if ((event.key === 'Enter' || event.key === ' ') && event.target.matches && event.target.matches(SELECTOR)) {
            event.preventDefault();
            open(event.target);
        }
    });
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => makeFocusable(document));
    } else {
        makeFocusable(document);
    }
})();
