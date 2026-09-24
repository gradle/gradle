/**
 * Everything a Markdown renderer needs that it cannot get from the MDX node
 * itself. The emitter is pure given a context, which is what makes the
 * renderers unit-testable without an Astro build (see renderers.test.ts).
 */
export interface EmitContext {
  /** Absolute site base for this build, e.g. `https://docs.gradle.org/9.8.0`. */
  base: string;

  /** Page being emitted, site-root-relative, e.g. `/userguide/reference/java-plugin/`. */
  pagePath: string;

  /**
   * Resolves an `<Xref page section>` to a site-root-relative path with an
   * optional `#anchor`. Mirrors Xref.astro, including its page-title and
   * unresolved-section fallbacks, so the `.md` and the HTML agree.
   */
  resolveXref(page: string, section?: string | string[]): Promise<string>;

  /** Build-script sample variants for `<SampleScripts>`, in DSL order. */
  loadScripts(args: {
    sample: string;
    basename: string;
    subpath?: string;
    tag?: string;
  }): Array<{ language: string; label: string; filename: string; content: string }>;

  /**
   * Non-buildscript sample file(s) for `<SampleFile>`. The converter tokenizes
   * per-DSL paths (`{lang}`, `{srcExt}`), so one tag can expand to several
   * variants; a plain path yields exactly one.
   */
  loadFile(args: {
    sample: string;
    path: string;
    lang?: string;
    tag?: string;
  }): Array<{ language: string; title: string; content: string }>;

  /** The Gradle version this build is for, e.g. `9.8.0`. */
  gradleVersion: string;

  /**
   * Maps an image import specifier (`./gradle-basic-1.png`, relative to the
   * page) to the URL Astro actually serves it at.
   *
   * It must resolve to the *optimized* asset, the same one `<Image>` puts in
   * the HTML. Referencing the unprocessed original instead makes Astro emit a
   * second copy of every image in its source format: 186 of 191 images landed
   * as both PNG and WebP, adding roughly 23 MB to the distribution for files
   * no page ever requested.
   */
  resolveAsset?(specifier: string): Promise<string | undefined>;

  /** Local import name → specifier, collected from the page's `import` lines. */
  imports?: Map<string, string>;
}

/** Turns a site-root-relative path or an already-absolute URL into an absolute URL. */
export function absolute(base: string, target: string): string {
  if (/^[a-z][a-z0-9+.-]*:/i.test(target) || target.startsWith("//")) {
    return target;
  }
  return base.replace(/\/+$/, "") + (target.startsWith("/") ? target : "/" + target);
}
