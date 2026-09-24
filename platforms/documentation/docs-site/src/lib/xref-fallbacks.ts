/**
 * CI-visible tracking for Xref section-anchor fallbacks.
 *
 * When <Xref section="..."> can't resolve a section anchor, the component
 * degrades to a page-level link (see Xref.astro). That behavior is deliberate
 * during the migration, but it used to be invisible outside a console.warn.
 *
 * This module makes it enforceable:
 *  - Xref.astro reports each fallback via `reportXrefFallback()`.
 *  - The `xrefFallbackReporter` Astro integration runs after `astro build`:
 *      - writes a full report to build/reports/xref-fallbacks.json (gitignored)
 *      - compares fallbacks against the committed baseline
 *        (xref-fallbacks-baseline.json at the repo root)
 *      - FAILS the build on any fallback not present in the baseline
 *      - logs a hint when baseline entries no longer occur (stale baseline)
 *  - `XREF_UPDATE_BASELINE=1 astro build` rewrites the baseline.
 *
 * Bootstrap: if no baseline file exists yet, the build does NOT fail; it
 * prints the fallbacks and tells you how to create the baseline. This keeps
 * the first CI run green until someone generates the baseline locally.
 *
 * `astro dev` is unaffected (the hook only runs on production builds).
 */
import { mkdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import type { AstroIntegration } from "astro";

export interface XrefFallback {
  /** Content-collection entry id of the target page, e.g. "fundamentals/gradle-basics/index.mdx" */
  entryId: string;
  /** The `page` prop that was passed to <Xref> */
  page: string;
  /** The `section` prop that failed to resolve */
  section: string | string[];
}

const STORE_KEY = Symbol.for("gradle-docs.xref-fallbacks");

function store(): XrefFallback[] {
  const g = globalThis as Record<symbol, unknown>;
  if (!g[STORE_KEY]) g[STORE_KEY] = [];
  return g[STORE_KEY] as XrefFallback[];
}

/** Stable identity for baseline comparison. */
export function fallbackKey(f: XrefFallback): string {
  const section = Array.isArray(f.section) ? f.section.join(" > ") : f.section;
  return `${f.entryId} :: ${section}`;
}

/** Called by Xref.astro whenever a section anchor degrades to a page link. */
export function reportXrefFallback(fallback: XrefFallback): void {
  store().push(fallback);
}

const BASELINE_FILE = fileURLToPath(new URL("../../xref-fallbacks-baseline.json", import.meta.url));
const REPORT_FILE = fileURLToPath(
  new URL("../../build/reports/xref-fallbacks.json", import.meta.url),
);

export function xrefFallbackReporter(): AstroIntegration {
  return {
    name: "xref-fallback-reporter",
    hooks: {
      "astro:build:done": async ({ logger }) => {
        const fallbacks = store();
        const keys = [...new Set(fallbacks.map(fallbackKey))].sort();

        // Always write the full report for humans/tooling.
        mkdirSync(dirname(REPORT_FILE), { recursive: true });
        writeFileSync(
          REPORT_FILE,
          JSON.stringify({ count: keys.length, fallbacks: keys }, null, 2) + "\n",
        );

        if (process.env.XREF_UPDATE_BASELINE) {
          writeFileSync(BASELINE_FILE, JSON.stringify(keys, null, 2) + "\n");
          logger.info(
            `baseline updated: ${keys.length} known fallback(s) written to xref-fallbacks-baseline.json`,
          );
          return;
        }

        if (!existsSync(BASELINE_FILE)) {
          if (keys.length > 0) {
            logger.warn(
              `${keys.length} Xref section fallback(s) detected, but no baseline exists yet — not failing the build.\n` +
                `  Review build/reports/xref-fallbacks.json, then run:\n` +
                `  XREF_UPDATE_BASELINE=1 npm run build   # commits the current state as the baseline`,
            );
          }
          return;
        }

        const baseline: string[] = JSON.parse(readFileSync(BASELINE_FILE, "utf8"));
        const baselineSet = new Set(baseline);
        const currentSet = new Set(keys);

        const regressions = keys.filter((k) => !baselineSet.has(k));
        const fixed = baseline.filter((k) => !currentSet.has(k));

        if (fixed.length > 0) {
          logger.info(
            `${fixed.length} baseline Xref fallback(s) no longer occur — consider refreshing the baseline ` +
              `(XREF_UPDATE_BASELINE=1 npm run build):\n  - ${fixed.join("\n  - ")}`,
          );
        }

        if (regressions.length > 0) {
          throw new Error(
            `${regressions.length} new Xref section fallback(s) — a section link silently degraded to a page link:\n` +
              `  - ${regressions.join("\n  - ")}\n` +
              `Fix the section reference (or the missing anchor), or if the fallback is expected, ` +
              `refresh the baseline with: XREF_UPDATE_BASELINE=1 npm run build`,
          );
        }

        logger.info(`Xref fallbacks: ${keys.length} (all known in baseline, 0 new)`);
      },
    },
  };
}
