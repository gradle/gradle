/**
 * Everything the version selector renders: reducing the services.gradle.org
 * payload to the stored index, mapping versions to their docs URLs, and building
 * the menu markup. Shared between the build-time render and the client-side
 * refresh so the two cannot drift apart. Unlike contract.ts this module is NOT
 * locked down; each published site version carries its own copy.
 */
import type { VersionsIndex } from "./local-storage";
import { GradleVersion } from "./gradle-version";

export const VERSIONS_ENDPOINT = "https://services.gradle.org/versions/all";
export const DOCS_BASE = "https://docs.gradle.org";

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
export function reduceVersions(raw: unknown, fetchedAt: number): VersionsIndex {
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
  return { schema: 1, fetchedAt, current, versions, activeRc, nightly };
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
 * at the bottom of their group. Index entries that fail to parse (possible
 * when the cache was written by a different site version) are dropped.
 */
export function renderVersionMenu(index: VersionsIndex, siteVersion: string | undefined): string {
  const entries: MenuEntry[] = [];
  for (const version of index.versions) {
    const parsed = GradleVersion.tryParse(version);
    if (!parsed) continue;
    entries.push({
      parsed,
      label: version,
      slot: version,
      current: version === siteVersion,
      special: false,
      badge: version === index.current ? "latest" : undefined,
    });
  }
  const rc = index.activeRc ? GradleVersion.tryParse(index.activeRc) : null;
  if (rc) {
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
  if (nightly) {
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

/** Finals first, then the specials (RC, nightly); newest first within each. */
function byGroupOrder(a: MenuEntry, b: MenuEntry): number {
  if (a.special !== b.special) return a.special ? 1 : -1;
  return b.parsed.compareTo(a.parsed);
}

/** Minimal HTML escaping; endpoint data must never reach the DOM as markup. */
function esc(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;");
}
