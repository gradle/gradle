(function() {
    const btn = document.querySelector('.theme-toggle');
    const themeMedia = window.matchMedia('(prefers-color-scheme: dark)');
    const darkHref = 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/styles/stackoverflow-dark.min.css';
    const lightHref = 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/styles/stackoverflow-light.min.css';
    const hljsStylesheet = document.getElementById('hljs-theme');

    const setTheme = (theme) => {
        document.documentElement.setAttribute('data-theme', theme);
        hljsStylesheet.href = theme === 'dark' ? darkHref : lightHref;
    };

    // Manual toggle
    btn.addEventListener('click', (e) => {
        e.preventDefault();
        setTheme(document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark');
    });

    // Always follow OS changes
    themeMedia.addEventListener('change', (e) => {
        setTheme(e.matches ? 'dark' : 'light');
    });
})();
