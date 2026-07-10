/**
 * Build-time-only loading of the Gradle releases index. Nothing here ships to
 * the client; the result is burnt into the page as the static menu markup.
 */
import { variables } from "../../config/variables";
import { reduceVersions, VERSIONS_ENDPOINT, type VersionsIndex } from "./version-menu";

let indexPromise: Promise<VersionsIndex> | undefined;

/**
 * Versions index fetched live from services.gradle.org once per build
 * (memoized across pages). When the endpoint is unreachable (offline dev),
 * the build degrades to an index containing only the site's own version and
 * relies on the client-side refresh to fill in the rest.
 */
export function loadVersionsIndex(): Promise<VersionsIndex> {
  return (indexPromise ??= fetchIndex());
}

async function fetchIndex(): Promise<VersionsIndex> {
  try {
    const response = await fetch(VERSIONS_ENDPOINT, { signal: AbortSignal.timeout(10_000) });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return reduceVersions(await response.json());
  } catch (error) {
    console.warn(
      `[version-selector] could not fetch ${VERSIONS_ENDPOINT} (${error}); ` +
        `burning in only the site's own version (${variables.gradleVersion})`,
    );
    return {
      current: variables.gradleVersion,
      versions: [variables.gradleVersion],
      activeRc: null,
      nightly: null,
    };
  }
}
