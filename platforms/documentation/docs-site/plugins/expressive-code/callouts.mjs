/**
 * Expressive Code plugin: AsciiDoc-style callouts.
 * https://docs.asciidoctor.org/asciidoc/latest/verbatim/callouts/
 *
 * Finds callout markers in trailing comments of code lines — both the shape
 * the converter emits (`// (1)`) and raw AsciiDoc conums in untouched snippet
 * files (`// <1>`), also `#` and `<!-- … -->` comment styles — removes the
 * comment, and renders a numbered bubble at the end of the line. Pair with
 * the <CalloutList> component for the explanation list below the code block.
 *
 * A comment leader is required, so `foo(1)` in real code never matches.
 * Exception: fences flagged with the `callouts` meta option (emitted by the
 * converter for listings that carry a callout list — e.g. directory trees or
 * console output, where markers have no comment leader) also match bare
 * trailing markers.
 *
 * Plain .mjs (not .ts): this file is imported by ec.config.mjs, which is
 * loaded outside of Vite, so it cannot use TypeScript. Imports resolve
 * against the @expressive-code/core copy hoisted by @astrojs/starlight
 * (0.41.x); do not add a differently-versioned explicit dependency or EC
 * will exist twice.
 */
import { definePlugin, ExpressiveCodeAnnotation } from "@expressive-code/core";
import { h } from "@expressive-code/core/hast";

/** `// (1) (2)`, `# <3>`, `<!-- (4) -->`, or auto-numbered `// <.>` at end of line. */
const CALLOUT_COMMENT = /(^|\s)(?:\/\/|#|<!--)\s*((?:[(<](?:\d+|\.)[)>]\s*)+)(?:-->\s*)?$/;

/** Asciidoctor's XML conum form: `<!--1-->` / `<!--.-->` (bare digit, no parens). */
const XML_CALLOUTS = /(^|\s)((?:<!--\s*(?:\d+|\.)\s*-->\s*)+)$/;

/** Bare markers at end of line with no comment leader (opt-in via meta). */
const BARE_CALLOUTS = /(^|\s)((?:[(<](?:\d+|\.)[)>]\s*)+)$/;

/**
 * Pure matcher, exported for unit tests. Tokens are explicit numbers or `'.'`
 * for AsciiDoc auto-numbered conums (`<.>`), which the caller resolves to a
 * per-block sequential counter.
 * @param {string} lineText
 * @param {boolean} [allowBare] also match markers without a comment leader
 * @returns {{ commentStart: number, tokens: (number | '.')[] } | null}
 */
export function matchCalloutComment(lineText, allowBare = false) {
  let m = lineText.match(CALLOUT_COMMENT);
  if (!m) {
    m = lineText.match(XML_CALLOUTS);
  }
  if (!m && allowBare) {
    m = lineText.match(BARE_CALLOUTS);
  }
  if (!m || m.index === undefined) {
    return null;
  }
  const tokens = Array.from(m[2].matchAll(/\d+|\./g), (d) => (d[0] === "." ? "." : Number(d[0])));
  if (tokens.length === 0) {
    return null;
  }
  return { commentStart: m.index + m[1].length, tokens };
}

class CalloutAnnotation extends ExpressiveCodeAnnotation {
  /**
   * @param {ConstructorParameters<typeof ExpressiveCodeAnnotation>[0]} options
   * @param {string} digits the callout number to display
   */
  constructor(options, digits) {
    super(options);
    this.digits = digits;
  }

  /** @param {import('@expressive-code/core').AnnotationRenderOptions} options */
  render({ nodesToTransform }) {
    // Replace the node (which carries the syntax highlighter's inline color —
    // digits get number-literal styling) with our own plain-text bubble.
    return nodesToTransform.map(() => h("span.gd-callout", this.digits));
  }
}

export function pluginCallouts() {
  return definePlugin({
    name: "gradle-callouts",
    baseStyles: `
      .gd-callout {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 1.35rem;
        height: 1.35rem;
        padding-inline: 0.25rem;
        margin-inline-start: 0.4rem;
        vertical-align: -0.3em;
        border-radius: 999px;
        background: var(--gd-callout-bg, var(--sl-color-white));
        color: var(--gd-callout-fg, var(--sl-color-black));
        font-size: 0.8rem;
        font-weight: 700;
        line-height: 1;
        font-family: var(--sl-font, sans-serif);
        user-select: none;
      }
    `,
    hooks: {
      preprocessCode: ({ codeBlock }) => {
        const allowBare = codeBlock.metaOptions?.getBoolean?.("callouts") ?? false;
        let autoNumber = 0;
        for (const line of codeBlock.getLines()) {
          const match = matchCalloutComment(line.text, allowBare);
          if (!match) {
            continue;
          }
          // Drop the whole trailing comment (and any spaces before it) …
          let cut = match.commentStart;
          while (cut > 0 && line.text[cut - 1] === " ") {
            cut--;
          }
          line.editText(cut, line.text.length, "");
          // … then append each number as text wrapped in a bubble annotation.
          for (const token of match.tokens) {
            const num = token === "." ? ++autoNumber : token;
            autoNumber = Math.max(autoNumber, num);
            const digits = String(num);
            const start = line.text.length;
            line.editText(start, start, digits);
            line.addAnnotation(
              new CalloutAnnotation(
                { inlineRange: { columnStart: start, columnEnd: start + digits.length } },
                digits,
              ),
            );
          }
        }
      },
    },
  });
}
