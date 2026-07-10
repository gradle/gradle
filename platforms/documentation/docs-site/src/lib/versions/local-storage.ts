/**
 * The version-selector localStorage contract
 *
 * Every published version of the docs site burns in its own copy of the
 * selector, but they all share one localStorage record. This module defines
 * exactly that shared surface and nothing else: the storage key, the stored
 * shape, and the load/store logic. Everything else (menu rendering, URL
 * mapping, reduction policy) lives in sibling modules and may evolve freely
 * per site version.
 *
 * Rules:
 * - Never change the meaning of existing `STORAGE_KEY` data. If the shape
 *   must change, bump the key suffix (`.v2`) and the `schema` field, and keep
 *   reading old keys best-effort.
 * - Loading must never throw and must reject anything that fails validation;
 *   callers fall back to the version list burnt in at build time.
 */

export const STORAGE_KEY = "gradle-docs.versions.v1";
export const CACHE_TTL_MS = 24 * 60 * 60 * 1000; // 1 day

export interface VersionsIndex {
  schema: 1;
  /** Epoch millis of the fetch that produced this index. */
  fetchedAt: number;
  /** The latest GA release ("current" on services.gradle.org). */
  current: string;
  /** Latest final release per minor, sorted newest first. */
  versions: string[];
  /** The active release candidate, if any. Published under its own slot. */
  activeRc: string | null;
  /** The latest nightly from master. Published under the `nightly` slot. */
  nightly: string | null;
}

export function isVersionsIndex(value: unknown): value is VersionsIndex {
  const index = value as VersionsIndex;
  return (
    index?.schema === 1 &&
    typeof index.fetchedAt === "number" &&
    typeof index.current === "string" &&
    Array.isArray(index.versions) &&
    index.versions.every((v) => typeof v === "string") &&
    (index.activeRc === null || typeof index.activeRc === "string") &&
    (index.nightly === null || typeof index.nightly === "string")
  );
}

/** Validated read of the shared index; undefined on anything unexpected. */
export function loadStoredIndex(): VersionsIndex | undefined {
  try {
    const parsed: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "");
    return isVersionsIndex(parsed) ? parsed : undefined;
  } catch {
    return undefined;
  }
}

/** Best-effort write of the shared index (storage may be full or blocked). */
export function storeIndex(index: VersionsIndex): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(index));
  } catch {
    // Losing the cache only costs a refetch on the next page view.
  }
}

export function isFresh(index: VersionsIndex, now: number): boolean {
  return now - index.fetchedAt < CACHE_TTL_MS;
}
