/**
 * AsciiDoc-style tagged-region handling for sample snippets.
 *
 * Snippet files use comment markers to delimit excerpt regions:
 *
 *   // tag::use-and-configure-plugin[]
 *   plugins { … }
 *   // end::use-and-configure-plugin[]
 *
 * `include::sample[…files="build.gradle.kts[tags=foo]"]` displays only the
 * lines between `tag::foo[]` and `end::foo[]` (the converter forwards this as
 * the `tag` prop on <SampleScripts>/<SampleFile>). Marker lines themselves are
 * never displayed — matching Asciidoctor, which strips all tag directives even
 * when including a whole file.
 */

/** Matches a tag directive anywhere in a line (inside any comment style). */
const TAG_DIRECTIVE = /\b(tag|end)::([\w.-]+)\[\]/;

/** Removes all tag/end directive lines; used when displaying whole files. */
export function stripTagMarkers(content: string): string {
  return content
    .split("\n")
    .filter((line) => !TAG_DIRECTIVE.test(line))
    .join("\n");
}

/**
 * Extracts the region(s) selected by an AsciiDoc `tags=` value. Multiple tags
 * may be separated by `;` or `,`; negated entries (`!foo`) are ignored.
 * Multiple regions with the same tag concatenate, as in Asciidoctor.
 *
 * @throws if no selected region exists in the content
 */
export function extractTaggedRegion(content: string, tagSpec: string, context = "sample"): string {
  const wanted = new Set(
    tagSpec
      .split(/[;,]/)
      .map((t) => t.trim())
      .filter((t) => t.length > 0 && !t.startsWith("!")),
  );
  if (wanted.size === 0) {
    return stripTagMarkers(content);
  }

  const out: string[] = [];
  let active = 0;
  for (const line of content.split("\n")) {
    const m = line.match(TAG_DIRECTIVE);
    if (m) {
      if (wanted.has(m[2])) {
        active += m[1] === "tag" ? 1 : -1;
      }
      continue;
    }
    if (active > 0) {
      out.push(line);
    }
  }

  while (out.length > 0 && out[0].trim() === "") {
    out.shift();
  }
  while (out.length > 0 && out[out.length - 1].trim() === "") {
    out.pop();
  }

  if (out.length === 0) {
    throw new Error(
      `Tag(s) '${tagSpec}' matched no content in ${context} — check the tag::…[]/end::…[] markers in the snippet file.`,
    );
  }
  return out.join("\n");
}

/**
 * Applies tag selection when present, otherwise strips marker lines.
 *
 * A tag that matches nothing falls back to the whole file (markers stripped)
 * with a console warning instead of failing the build: ~30 legacy snippet
 * references in gradle/gradle name tags that don't exist in the snippet file,
 * which Asciidoctor also tolerated. Fix those upstream; this keeps the pages
 * rendering meanwhile.
 */
export function selectSampleContent(
  content: string,
  tag: string | undefined,
  context: string,
): string {
  if (!tag) {
    return stripTagMarkers(content);
  }
  try {
    return extractTaggedRegion(content, tag, context);
  } catch (error) {
    console.warn(
      `[sample-tags] ${error instanceof Error ? error.message : error} — falling back to the whole file.`,
    );
    return stripTagMarkers(content);
  }
}
