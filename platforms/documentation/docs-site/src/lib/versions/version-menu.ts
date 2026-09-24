/**
 * Everything the version selector renders: reducing the services.gradle.org
 * payload to a compact index, mapping versions to their docs URLs, and building
 * the menu markup. Shared between the build-time render and the client-side
 * refresh so the two cannot drift apart. Each published site version carries
 * its own copy; nothing here is shared between site versions.
 *
 * There is deliberately no client-side cache: the endpoint is served from a
 * CDN with `Cache-Control` and `ETag`, so freshness and traffic are the
 * server's knobs, adjustable for every published site version at once.
 */
import { GradleVersion } from "./gradle-version";

export const VERSIONS_ENDPOINT = "https://services.gradle.org/versions/all";
export const DOCS_BASE = "https://docs.gradle.org";

/** Compact reduction of the `/versions/all` payload the selector works with. */
export interface VersionsIndex {
  /** The latest GA release ("current" on services.gradle.org). */
  current: string;
  /** Latest final release per minor, sorted newest first. */
  versions: string[];
  /** The active release candidate, if any. Published under its own slot. */
  activeRc: string | null;
  /** The latest nightly from master. Published under the `nightly` slot. */
  nightly: string | null;
}

interface RawVersionEntry {
  version: string;
  final: boolean;
  current: boolean;
  activeRc: boolean;
  nightly: boolean;
}

/**
 * Reduce the raw `/versions/all` payload (516+ entries, ordered by build time,
 * not by version) to the compact index the selector works with: the latest
 * final release of every minor, sorted descending, plus the active RC and the
 * latest nightly. Entries GradleVersion cannot parse are skipped.
 *
 * Throws on payloads it cannot make sense of; callers keep their previous
 * index in that case.
 */
export function reduceVersions(raw: unknown): VersionsIndex {
  if (!Array.isArray(raw)) throw new Error("versions payload is not an array");
  const entries = raw.filter((e): e is RawVersionEntry => typeof e?.version === "string");

  const latestPerMinor = new Map<string, GradleVersion>();
  for (const entry of entries) {
    if (entry.final !== true) continue;
    const parsed = GradleVersion.tryParse(entry.version);
    if (!parsed) continue;
    const seen = latestPerMinor.get(parsed.minor);
    if (!seen || parsed.compareTo(seen) > 0) latestPerMinor.set(parsed.minor, parsed);
  }

  const versions = [...latestPerMinor.values()]
    .sort((a, b) => b.compareTo(a))
    .map((gv) => gv.version);
  if (versions.length === 0) throw new Error("versions payload contains no final releases");

  const current = entries.find((e) => e.current === true)?.version ?? versions[0];
  const activeRc = entries.find((e) => e.activeRc === true)?.version ?? null;
  const nightly = entries.find((e) => e.nightly === true)?.version ?? null;
  return { current, versions, activeRc, nightly };
}

/**
 * User-manual index of a published docs version. Always the index, never the
 * current page: page names shift between versions, and `userguide.html` is the
 * one URL that exists in every version ever published (0.9 through nightly),
 * unlike `index.html` or the bare `userguide/` directory, which 404 on older
 * versions.
 */
export function versionDocsUrl(version: string): string {
  return `${DOCS_BASE}/${version}/userguide/userguide.html`;
}

/** The same page under the `current` alias, e.g. `/current/userguide/x/`. */
export function currentDocsUrl(pagePath: string): string {
  return `${DOCS_BASE}/current${pagePath.startsWith("/") ? pagePath : `/${pagePath}`}`;
}

interface MenuEntry {
  parsed: GradleVersion;
  label: string;
  /** Publication slot the entry links to (differs from version for nightly). */
  slot: string;
  current: boolean;
  /** Pre-release entries (RC, nightly) sort below the finals of their major. */
  special: boolean;
  badge?: string;
}

/**
 * Menu markup for the version selector: `<li>` items for the menu's `<ul>`,
 * grouped by major, finals newest-first with the pre-releases (RC, nightly)
 * at the bottom of their group. Index entries that fail to parse are dropped
 * (defensive: the index derives from external endpoint data).
 */
export function renderVersionMenu(
  index: VersionsIndex,
  siteVersion: string | undefined,
  pagePath = "/",
): string {
  // Only versions at or below this build belong in the list. Everything older
  // than a release already exists when it is built, so the list is complete
  // and stays correct forever — the past does not change. It also makes the
  // output reproducible: rebuilding 9.7.0 a year from now produces the same
  // menu it did on release day, rather than picking up whatever has shipped
  // since. Anything newer is reached through the Current entry instead.
  const site = siteVersion ? GradleVersion.tryParse(siteVersion) : null;
  const atOrBelowSite = (v: GradleVersion) => !site || v.compareTo(site) <= 0;

  // Listing every release would put 95 entries in a dropdown. Instead the list
  // follows Gradle's own end-of-life policy (see the Feature Lifecycle page),
  // which already defines the lines that matter to a reader:
  //
  //   - each minor release makes earlier minors of the same major end-of-life;
  //   - a new major puts the previous major into maintenance (critical and
  //     security fixes only) and ends the life of the one before it.
  //
  // So the menu carries the recent minors of the current major, plus the last
  // minor of each of the two preceding majors, and leaves everything older to
  // the "All releases" link. Today that is 9.7.1, 9.6.1, 9.5.1, 8.14.5, 7.6.6.
  const RECENT_MINORS_OF_CURRENT_MAJOR = 3;

  // Newest patch per (major, minor), newest first.
  const perMinor = new Map<string, GradleVersion>();
  for (const version of index.versions) {
    const parsed = GradleVersion.tryParse(version);
    if (!parsed || !atOrBelowSite(parsed)) continue;
    const key = `${parsed.majorVersion}.${minorOf(parsed)}`;
    const held = perMinor.get(key);
    if (!held || parsed.compareTo(held) > 0) perMinor.set(key, parsed);
  }
  // Seed the build's own version so its minor is represented by the version
  // actually being built. `reduceVersions` keeps only the newest patch per
  // minor, so a 9.7.0 build would otherwise see the 9.7 line filtered out as
  // newer than itself and fall off the shortlist, pushing an extra older minor
  // into its place.
  if (site) {
    perMinor.set(`${site.majorVersion}.${minorOf(site)}`, site);
  }

  const byMajor = new Map<number, GradleVersion[]>();
  for (const version of perMinor.values()) {
    const list = byMajor.get(version.majorVersion) ?? [];
    list.push(version);
    byMajor.set(version.majorVersion, list);
  }
  for (const list of byMajor.values()) list.sort((a, b) => b.compareTo(a));
  const majors = [...byMajor.keys()].sort((a, b) => b - a);

  const selected: Array<{ version: GradleVersion; badge?: string }> = [];
  for (const version of (byMajor.get(majors[0]) ?? []).slice(0, RECENT_MINORS_OF_CURRENT_MAJOR)) {
    selected.push({ version });
  }
  // Naming the support status is the point: a reader on 7.6 should be able to
  // see that the line is end-of-life without leaving the page.
  const previousMajor = (byMajor.get(majors[1]) ?? [])[0];
  if (previousMajor) selected.push({ version: previousMajor, badge: "maintenance" });
  const majorBeforeThat = (byMajor.get(majors[2]) ?? [])[0];
  if (majorBeforeThat) selected.push({ version: majorBeforeThat, badge: "EOL" });

  const entries: MenuEntry[] = selected.map(({ version, badge }) => ({
    parsed: version,
    label: version.version,
    slot: version.version,
    current: version.version === siteVersion,
    special: false,
    badge: version.version === index.current ? "latest" : badge,
  }));

  // The build's own version must always be listed. It may fall outside the
  // shortlist entirely — an 8.3 page is neither a recent minor of the current
  // major nor the last minor of its own — and `reduceVersions` keeps only the
  // newest patch per minor, so a 9.7.0 build would otherwise be absent from
  // its own dropdown once 9.7.1 shipped.
  if (site && !entries.some((e) => e.label === siteVersion)) {
    entries.push({
      parsed: site,
      label: site.version,
      slot: site.version,
      current: true,
      special: false,
    });
  }

  const rc = index.activeRc ? GradleVersion.tryParse(index.activeRc) : null;
  if (rc && atOrBelowSite(rc)) {
    entries.push({
      parsed: rc,
      label: rc.version,
      slot: rc.version,
      current: rc.version === siteVersion,
      special: true,
      badge: "RC",
    });
  }
  const nightly = index.nightly ? GradleVersion.tryParse(index.nightly) : null;
  if (nightly && atOrBelowSite(nightly)) {
    // The major group already places the nightly; the full version string
    // (9.7.0-20260706133305+0000) would only add noise. Links to the rolling
    // `nightly` slot.
    entries.push({
      parsed: nightly,
      label: "Nightly",
      slot: "nightly",
      current: false,
      special: true,
    });
  }

  const groups = new Map<number, MenuEntry[]>();
  for (const entry of entries) {
    const group = groups.get(entry.parsed.majorVersion);
    if (group) group.push(entry);
    else groups.set(entry.parsed.majorVersion, [entry]);
  }

  const parts: string[] = [];

  // Forward navigation needs one link, not a list. `/current/` is a stable
  // alias, so the entry carries no version number and can never go stale: a
  // page built years ago still points at whatever the latest release is. It
  // targets the same page, which the redirect stubs resolve across renames; a
  // page that no longer exists 404s, which is a more honest answer than
  // silently dropping the reader at the documentation root.
  parts.push(
    `<li class="current-version"><a href="${esc(currentDocsUrl(pagePath))}">Current</a></li>`,
  );

  for (const major of [...groups.keys()].sort((a, b) => b - a)) {
    parts.push(`<li class="major-header" aria-hidden="true">Gradle ${major}</li>`);
    for (const entry of groups.get(major)!.sort(byGroupOrder)) {
      parts.push(
        `<li><a href="${esc(versionDocsUrl(entry.slot))}"${entry.current ? ' aria-current="true"' : ""}>${esc(entry.label)}${
          entry.badge ? `<span class="badge">${esc(entry.badge)}</span>` : ""
        }</a></li>`,
      );
    }
  }

  parts.push(
    `<li class="all-releases"><a href="https://gradle.org/releases/">All releases</a></li>`,
  );
  return parts.join("");
}

/** Minor component of a version, via its string form (GradleVersion exposes major only). */
function minorOf(version: GradleVersion): number {
  const parts = version.version.split(".");
  return Number.parseInt(parts[1] ?? "0", 10) || 0;
}

/** Finals first, then the specials (RC, nightly); newest first within each. */
function byGroupOrder(a: MenuEntry, b: MenuEntry): number {
  if (a.special !== b.special) return a.special ? 1 : -1;
  return b.parsed.compareTo(a.parsed);
}

/** Minimal HTML escaping; endpoint data must never reach the DOM as markup. */
function esc(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;");
}
