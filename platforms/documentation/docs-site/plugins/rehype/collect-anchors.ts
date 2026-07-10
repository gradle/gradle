import Slugger from "github-slugger";
import { SKIP, visit } from "unist-util-visit";
import type { Root } from "hast";
import type { VFile } from "vfile";

/**
 * A single anchor-targetable element on a page (heading or `<dt>`).
 *
 * `slug` is the URL fragment after `#`.
 * `path` is the chain of heading text from the page's `<h1>` down to this
 *   anchor's leaf, leaf last. For `<dt>`, the term itself is the leaf and the
 *   preceding heading chain is the surrounding context. It's a chain rather
 *   than just the leaf so `<Xref>` can disambiguate duplicate section titles
 *   on a page via `section={["Parent", "Leaf"]}`.
 */
export interface AnchorEntry {
  slug: string;
  path: string[];
}

interface StackFrame {
  depth: number;
  text: string;
}

const HEADING_TAGS = new Set(["h1", "h2", "h3", "h4", "h5", "h6"]);

/**
 * Collects every anchor-targetable element (`h1`–`h6`, `dt`) on a page into
 * `file.data.astro.frontmatter.anchors`, assigning `id`s where missing.
 *
 * This is what makes `<Xref>` work: the migrated corpus cross-references
 * sections by human-readable heading text (carried over from AsciiDoc), and
 * `Xref.astro` resolves that text to a real `#fragment` by reading this table
 * via `render(entry).remarkPluginFrontmatter`.
 *
 * Astro's built-ins (`rehypeHeadingIds`, `render(entry).headings`) don't
 * cover what `<Xref>` needs:
 *
 * - `<dt>` terms are never slugged, but AsciiDoc xrefs target them (e.g. the
 *   glossary). They also arrive as `mdxJsxFlowElement` JSX nodes rather than
 *   hast `element`s, so we handle both shapes.
 * - There's no heading hierarchy — we record the `h1 → … → leaf` path so
 *   `<Xref>` can disambiguate duplicate section titles on a page.
 * - Since our slugs share a namespace with Astro's heading ids, we must
 *   mirror its slugging exactly (fresh `github-slugger` per file, one
 *   `.slug()` call per node, ids only set when absent) — any drift and
 *   `<Xref>` emits fragments that don't exist.
 * - Text extraction includes `<code>` descendants and skips MDX expressions,
 *   matching the section labels the AsciiDoc-to-MDX converter emits in
 *   `<Xref section="…">`.
 */
export function rehypeCollectAnchors() {
  return function plugin() {
    return transform;
  };

  function transform(tree: Root, file: VFile): void {
    const filePath = file.history[0] ?? file.path ?? "";
    const isMDX = filePath.endsWith(".mdx");
    const slugger = new Slugger();
    const stack: StackFrame[] = [];
    const anchors: AnchorEntry[] = [];

    visit(tree, (node: any) => {
      const tagName = elementName(node);
      if (!tagName) return;

      if (HEADING_TAGS.has(tagName)) {
        const depth = Number.parseInt(tagName.slice(1), 10);
        const text = extractText(node, isMDX);

        while (stack.length > 0 && stack[stack.length - 1].depth >= depth) {
          stack.pop();
        }
        stack.push({ depth, text });

        const slug = slugger.slug(text);
        if (!hasIdAttribute(node)) {
          setIdAttribute(node, slug);
        }

        anchors.push({ slug, path: stack.map((s) => s.text) });
        return;
      }

      if (tagName === "dt") {
        const text = extractText(node, isMDX);
        const slug = slugger.slug(text);
        setIdAttribute(node, slug);
        anchors.push({
          slug,
          path: [...stack.map((s) => s.text), text],
        });
        // Intentionally do not push onto the stack — `<dt>` inherits its
        // section context but doesn't open a new one.
      }
    });

    file.data.astro ??= {} as Record<string, unknown>;
    const astroData = file.data.astro as { frontmatter?: Record<string, unknown> };
    astroData.frontmatter ??= {};
    astroData.frontmatter.anchors = anchors;
  }
}

/**
 * Normalizes the tag name across the three node shapes we care about:
 *
 *   - hast `element` → `node.tagName`
 *   - `mdxJsxFlowElement` / `mdxJsxTextElement` → `node.name`
 *
 * Anything else returns `undefined` and is ignored by the caller.
 */
function elementName(node: any): string | undefined {
  if (!node || typeof node !== "object") return undefined;
  if (node.type === "element" && typeof node.tagName === "string") return node.tagName;
  if (
    (node.type === "mdxJsxFlowElement" || node.type === "mdxJsxTextElement") &&
    typeof node.name === "string"
  ) {
    return node.name;
  }
  return undefined;
}

function hasIdAttribute(node: any): boolean {
  if (node.type === "element") {
    return typeof node.properties?.id === "string";
  }
  if (Array.isArray(node.attributes)) {
    return node.attributes.some(
      (a: any) => a?.type === "mdxJsxAttribute" && a.name === "id" && typeof a.value === "string",
    );
  }
  return false;
}

function setIdAttribute(node: any, id: string): void {
  if (node.type === "element") {
    node.properties = node.properties ?? {};
    node.properties.id = id;
    return;
  }
  // mdxJsxFlowElement / mdxJsxTextElement
  node.attributes = node.attributes ?? [];
  const existing = node.attributes.find(
    (a: any) => a?.type === "mdxJsxAttribute" && a.name === "id",
  );
  if (existing) {
    existing.value = id;
  } else {
    node.attributes.push({ type: "mdxJsxAttribute", name: "id", value: id });
  }
}

/**
 * Extracts the plain-text content of a heading/`<dt>` node by concatenating
 * every text descendant. Text inside `<code>` and other elements is included
 * so the path leaf matches what readers (and the AsciiDoc-to-MDX converter)
 * see as the heading label.
 *
 * The one exception is MDX expression children (`{foo}` in JSX bodies and
 * `mdxJsxTextExpression` nodes) — these get rendered as JS expressions, not
 * literal text, so we skip them.
 */
function extractText(node: any, isMDX: boolean): string {
  let text = "";
  visit(node, (child: any) => {
    if (child === node) return;
    // Skip MDX expression bodies — they emit JS, not user-readable text.
    if (child.type === "mdxTextExpression" || child.type === "mdxFlowExpression") {
      return SKIP;
    }
    if (child.type === "text" || child.type === "raw") {
      const value = typeof child.value === "string" ? child.value : "";
      if (child.type === "raw" && /^\n?<.*>\n?$/.test(value)) {
        return;
      }
      if (isMDX) {
        text += value;
      } else {
        text += value.replace(/\{/g, "${");
      }
    }
  });
  return text.trim();
}
