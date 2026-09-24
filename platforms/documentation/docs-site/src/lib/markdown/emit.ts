/**
 * Turns a page's MDX body into the plain-Markdown sibling (`index.md`) served
 * next to `index.html` for agents.
 *
 * The transform runs on the MDX syntax tree, never on rendered HTML. That is
 * the whole point: converting a finished page back to Markdown inherits the
 * page's chrome — navigation lists, JSON-LD, stray version text, leaked tags —
 * which is exactly what Cloudflare's on-the-fly conversion does today and why
 * its output is unusable as a canonical representation. Rendering from source
 * means none of that exists to strip.
 */
import { unified } from "unified";
import remarkParse from "remark-parse";
import remarkGfm from "remark-gfm";
import remarkMdx from "remark-mdx";
import { toMarkdown } from "mdast-util-to-markdown";
import { gfmToMarkdown } from "mdast-util-gfm";
import type { EmitContext } from "./context";
import { absolute } from "./context";
import { hasRenderer, renderers, type JsxNode } from "./renderers";

const parser = unified().use(remarkParse).use(remarkGfm).use(remarkMdx);

interface Node {
  type: string;
  name?: string | null;
  value?: string;
  children?: Node[];
  lang?: string | null;
  url?: string;
}

const JSX_TYPES = new Set(["mdxJsxFlowElement", "mdxJsxTextElement"]);
const DROPPED_TYPES = new Set([
  // `import { Xref } from '#/components'` — noise to an agent.
  "mdxjsEsm",
  // `{/* converter-gap: ... */}` and other embedded expressions; they render
  // as JavaScript, not text.
  "mdxFlowExpression",
  "mdxTextExpression",
]);

function stringify(children: Node[]): string {
  return toMarkdown({ type: "root", children } as never, {
    extensions: [gfmToMarkdown()],
    bullet: "-",
    fences: true,
    rule: "-",
  });
}

/** Strips a leading YAML frontmatter block, if the caller passed a whole file. */
export function stripFrontmatter(source: string): string {
  return source.startsWith("---") ? source.replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, "") : source;
}

async function transform(nodes: Node[], ctx: EmitContext, page: string): Promise<Node[]> {
  const out: Node[] = [];
  for (const node of nodes) {
    if (DROPPED_TYPES.has(node.type)) continue;

    if (JSX_TYPES.has(node.type)) {
      const name = node.name ?? "(fragment)";
      if (node.name === null) {
        // A bare <>…</> fragment carries no meaning of its own.
        out.push(...(await transform(node.children ?? [], ctx, page)));
        continue;
      }
      if (!hasRenderer(name)) {
        throw new Error(
          `${page}: no Markdown renderer for <${name}>.\n` +
            `Every component that can reach a page needs an entry in src/lib/markdown/renderers.ts, ` +
            `otherwise the .md output would silently lose it. Add one that says what <${name}> means to an agent.`,
        );
      }
      const children = async () => stringify(await transform(node.children ?? [], ctx, page));
      const value = await renderers[name](node as unknown as JsxNode, ctx, children);
      out.push({ type: "html", value });
      continue;
    }

    // Plain Markdown links and images written in the source are page-relative
    // or site-root-relative, which means nothing in a standalone .md fetched
    // on its own. Absolutise them the same way the component renderers do.
    const withUrl =
      (node.type === "link" || node.type === "image") && typeof node.url === "string"
        ? { ...node, url: absolute(ctx.base, resolveAgainstPage(ctx.pagePath, node.url)) }
        : node;

    if (withUrl.children?.length) {
      out.push({ ...withUrl, children: await transform(withUrl.children, ctx, page) });
      continue;
    }
    out.push(withUrl);
  }
  return out;
}

export interface EmitResult {
  markdown: string;
  /** Code fences that carry no language tag, by their first line of content. */
  untaggedFences: string[];
  /** JSX element names still in the tree after the transform — always a bug. */
  survivingJsx: string[];
}

export async function emitMarkdown(
  body: string,
  ctx: EmitContext,
  page = ctx.pagePath,
): Promise<EmitResult> {
  const tree = parser.parse(stripFrontmatter(body)) as unknown as Node;
  // Import lines are dropped from the output, but an <Image src={foo}> refers
  // to one by its local name, so the bindings have to be read first.
  const withImports: EmitContext = { ...ctx, imports: collectImports(tree.children ?? []) };
  const children = await transform(tree.children ?? [], withImports, page);

  // Agents key off the fence language, and a bare fence tells them nothing.
  // The AsciiDoc sources often have no language either, so rather than failing
  // the build on pre-existing content we tag them `text` — which is what they
  // are, usually console output — and report the count so the sources can be
  // improved over time.
  const untaggedFences: string[] = [];
  tagBareFences(children, untaggedFences);

  const survivingJsx: string[] = [];
  collectSurvivingJsx(children, survivingJsx);

  const markdown =
    stringify(children)
      .replace(/\n{3,}/g, "\n\n")
      .trim() + "\n";
  return { markdown, untaggedFences, survivingJsx };
}

const IMPORT_BINDING = /import\s+([A-Za-z_$][\w$]*)\s+from\s+['"]([^'"]+)['"]/g;

/** Reads `import name from './file.png'` bindings out of the page's ESM nodes. */
function collectImports(nodes: Node[]): Map<string, string> {
  const map = new Map<string, string>();
  for (const node of nodes) {
    if (node.type !== "mdxjsEsm" || typeof node.value !== "string") continue;
    for (const m of node.value.matchAll(IMPORT_BINDING)) map.set(m[1], m[2]);
  }
  return map;
}

/** `./foo.png` and `foo.png` resolve against the page directory; `/x/` is already site-root. */
function resolveAgainstPage(pagePath: string, url: string): string {
  if (/^[a-z][a-z0-9+.-]*:/i.test(url) || url.startsWith("//")) return url;
  // A bare `#anchor` means "this page", which only holds while the reader is
  // on it. In a standalone .md it has to name the page explicitly.
  if (url.startsWith("#")) return pagePath.replace(/\/+$/, "/") + url;
  if (url.startsWith("/")) return url;
  const [path, hash] = url.split("#", 2);
  const joined = pagePath.replace(/\/+$/, "") + "/" + path.replace(/^\.\//, "");
  return hash ? `${joined}#${hash}` : joined;
}

/**
 * Detects JSX that survived the transform. This has to be an AST check, not a
 * regex over the output: the corpus legitimately contains `<Xref …>` as
 * literal text inside code spans, and a textual scan cannot tell that apart
 * from a component the transform failed to handle.
 */
function collectSurvivingJsx(nodes: Node[], into: string[]): void {
  for (const node of nodes) {
    if (JSX_TYPES.has(node.type)) into.push(node.name ?? "(fragment)");
    if (node.children?.length) collectSurvivingJsx(node.children, into);
  }
}

function tagBareFences(nodes: Node[], into: string[]): void {
  for (const node of nodes) {
    if (node.type === "code" && !node.lang) {
      into.push((node.value ?? "").split("\n", 1)[0].slice(0, 60));
      node.lang = "text";
    }
    if (node.children?.length) tagBareFences(node.children, into);
  }
}

/** Page-size guard: the spec's warn/fail budget, measured on the emitted Markdown. */
export const SIZE_WARN_BYTES = 64 * 1024;
export const SIZE_FAIL_BYTES = 192 * 1024;

export interface PageProblem {
  page: string;
  kind: "jsx-survived" | "relative-link" | "untagged-fence" | "too-large";
  detail: string;
}

/**
 * The build-time gates from the spec's acceptance criteria, applied to one
 * emitted page. Returns hard failures; size warnings are reported separately
 * by the caller so a large page does not fail a build on its own.
 */
export function validate(page: string, result: EmitResult): PageProblem[] {
  const problems: PageProblem[] = [];
  const { markdown } = result;

  for (const name of new Set(result.survivingJsx)) {
    problems.push({ page, kind: "jsx-survived", detail: `<${name}>` });
  }

  // A standalone .md has none of the page context relative URLs rely on.
  // Scan with fenced blocks and inline code removed so documented syntax in a
  // code span is not mistaken for a real link.
  const prose = markdown.replace(/```[\s\S]*?```/g, "").replace(/`[^`\n]*`/g, "");
  for (const m of prose.matchAll(/\]\((?!https?:|mailto:|#)([^)\s]+)/g)) {
    problems.push({ page, kind: "relative-link", detail: m[1] });
    break;
  }

  const bytes = Buffer.byteLength(markdown, "utf8");
  if (bytes > SIZE_FAIL_BYTES) {
    problems.push({
      page,
      kind: "too-large",
      detail: `${Math.round(bytes / 1024)} KB exceeds the ${SIZE_FAIL_BYTES / 1024} KB limit`,
    });
  }

  return problems;
}
