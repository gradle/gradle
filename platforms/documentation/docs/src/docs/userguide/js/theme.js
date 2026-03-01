(function() {
    const btn = document.querySelector('.theme-toggle');
    const themeMedia = window.matchMedia('(prefers-color-scheme: dark)');
    const darkHref = 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/styles/stackoverflow-dark.min.css';
    const lightHref = 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/styles/stackoverflow-light.min.css';
    const hljsStylesheet = document.getElementById('hljs-theme');

    const setTheme = (theme) => {
        document.documentElement.setAttribute('data-theme', theme);
        hljsStylesheet.href = theme === 'dark' ? darkHref : lightHref;
        try {
            localStorage.setItem('theme', theme);
        } catch (err) {
            // Ignore storage errors (e.g. disabled cookies/storage)
        }
    };

    // Manual toggle — marks an explicit user override
    btn.addEventListener('click', (e) => {
        e.preventDefault();
        const nextTheme = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        setTheme(nextTheme);
        try {
            localStorage.setItem('theme-manual-override', 'true');
        } catch (err) {
            // Ignore storage errors
        }
    });

    // Follow OS changes only when the user hasn't manually overridden
    themeMedia.addEventListener('change', (e) => {
        try {
            if (localStorage.getItem('theme-manual-override') !== null) {
                return;
            }
        } catch (err) {
            // If storage is unavailable, fall back to following OS
        }
        setTheme(e.matches ? 'dark' : 'light');
    });
})();
