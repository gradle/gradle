/**
 * Astro's `base` carries the version prefix this build publishes under, so
 * `Astro.url.pathname` arrives as `/9.9.0/userguide/…`. Anything that reads the
 * path as an identity within the site rather than as a URL — sample scoping,
 * the version selector's links to the same page in another version — wants it
 * without that prefix.
 */
export function sitePath(pathname: string): string {
  const base = import.meta.env.BASE_URL.replace(/\/+$/, "");
  if (base && (pathname === base || pathname.startsWith(`${base}/`))) {
    return pathname.slice(base.length) || "/";
  }
  return pathname;
}
