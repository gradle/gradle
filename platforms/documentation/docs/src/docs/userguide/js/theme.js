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

    // Manual toggle
    btn.addEventListener('click', (e) => {
        e.preventDefault();
        const nextTheme = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        setTheme(nextTheme);
    });

    // OS changes always apply and clear any saved preference
    themeMedia.addEventListener('change', (e) => {
        setTheme(e.matches ? 'dark' : 'light');
        try {
            localStorage.removeItem('theme');
        } catch (err) {
            // Ignore storage errors
        }
    });
})();
