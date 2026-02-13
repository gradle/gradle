(function() {
    'use strict';

    // Only run on desktop where TOC is fixed on right side
    if (window.innerWidth < 1200) return; // 75rem = 1200px

    // Get all section headings that have IDs
    const headings = document.querySelectorAll('.sect1 h2[id], .sect2 h3[id]');
    const tocLinks = document.querySelectorAll('#header #toc a');

    // Create a map of href -> link element for faster lookup
    const linkMap = new Map();
    tocLinks.forEach(link => {
        const href = link.getAttribute('href');
        if (href && href.startsWith('#')) {
            linkMap.set(href.substring(1), link);
        }
    });

    let activeId = null;

    function setActiveLink(id) {
        if (activeId === id) return;

        // Remove active class from all links
        tocLinks.forEach(link => link.classList.remove('active'));

        // Add active class to current link
        const activeLink = linkMap.get(id);
        if (activeLink) {
            activeLink.classList.add('active');
            activeId = id;
        }
    }

    const observerCallback = (entries) => {
        // Find the topmost intersecting heading
        let topmostEntry = null;
        let topmostY = Infinity;

        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const y = entry.boundingClientRect.top;
                if (y < topmostY) {
                    topmostY = y;
                    topmostEntry = entry;
                }
            }
        });

        if (topmostEntry) {
            setActiveLink(topmostEntry.target.id);
        }
    };

    // Create the observer
    const observer = new IntersectionObserver(observerCallback, {
        rootMargin: '-100px 0px -80% 0px', // Top 20% of viewport is active zone
        threshold: 0
    });

    // Observe all headings
    headings.forEach(heading => {
        if (heading.id) {
            observer.observe(heading);
        }
    });

    // Set initial active link on page load
    function findInitialActiveHeading() {
        let closestHeading = null;
        let closestDistance = Infinity;

        headings.forEach(heading => {
            const rect = heading.getBoundingClientRect();
            const distance = Math.abs(rect.top - 100);

            if (distance < closestDistance && rect.top < window.innerHeight) {
                closestDistance = distance;
                closestHeading = heading;
            }
        });

        if (closestHeading) {
            setActiveLink(closestHeading.id);
        }
    }

    findInitialActiveHeading();

    // Re-initialize on window resize
    let resizeTimer;
    window.addEventListener('resize', function() {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(findInitialActiveHeading, 250);
    });

})();
