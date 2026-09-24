/**
 * Markdown renderings for every element that can appear as JSX in the corpus.
 *
 * This registry is the definition of "done" for the per-page `.md` output: the
 * emitter throws on any JSX element without an entry here, so a component
 * cannot be added to `src/components/index.ts` (or pulled in from Starlight or
 * `astro:assets`) without someone deciding what it means to an agent.
 *
 * Renderers never see rendered HTML. They receive the MDX node and a
 * `children()` thunk that returns the already-transformed Markdown of the
 * element's body, so nesting works without any HTML round-trip.
 */
import type { EmitContext } from "./context";
import { absolute } from "./context";
import { javadocUrl, groovyDslUrl, kotlinDslUrl } from "#/components/link/api-doc-url";

/** An MDX JSX element node, narrowed to the parts renderers use. */
export interface JsxNode {
  type: "mdxJsxFlowElement" | "mdxJsxTextElement";
  name: string | null;
  attributes?: Array<{
    type: string;
    name?: string;
    value?: unknown;
  }>;
  children?: unknown[];
}

export type Children = () => Promise<string>;

export type Renderer = (
  node: JsxNode,
  ctx: EmitContext,
  children: Children,
) => string | Promise<string>;

/** Reads a literal string attribute; expression attributes return undefined. */
export function attr(node: JsxNode, name: string): string | undefined {
  const found = node.attributes?.find((a) => a.type === "mdxJsxAttribute" && a.name === name);
  if (!found) return undefined;
  const { value } = found;
  if (typeof value === "string") return value;
  // `attr={"literal"}` arrives as an expression node; take its raw source when
  // it is a plain string literal, otherwise treat it as absent.
  if (value && typeof value === "object" && "value" in value) {
    const raw = String((value as { value: unknown }).value).trim();
    const quoted = /^(['"])(.*)\1$/s.exec(raw);
    if (quoted) return quoted[2];
    const arrayish = /^\[[\s\S]*\]$/.test(raw);
    if (!arrayish) return raw;
  }
  return undefined;
}

/** Reads an attribute only when it is a plain string literal, never an expression. */
function literalAttr(node: JsxNode, name: string): string | undefined {
  const found = node.attributes?.find((a) => a.type === "mdxJsxAttribute" && a.name === name);
  return typeof found?.value === "string" ? found.value : undefined;
}

/** Reads an attribute that may be a string or a string array (`section`). */
function attrList(node: JsxNode, name: string): string | string[] | undefined {
  const found = node.attributes?.find((a) => a.type === "mdxJsxAttribute" && a.name === name);
  if (!found) return undefined;
  const { value } = found;
  if (typeof value === "string") return value;
  if (value && typeof value === "object" && "value" in value) {
    const raw = String((value as { value: unknown }).value).trim();
    if (/^\[[\s\S]*\]$/.test(raw)) {
      try {
        const parsed: unknown = JSON.parse(raw.replace(/'/g, '"'));
        if (Array.isArray(parsed)) return parsed.map(String);
      } catch {
        /* fall through to the string form below */
      }
    }
    const quoted = /^(['"])(.*)\1$/s.exec(raw);
    return quoted ? quoted[2] : raw;
  }
  return undefined;
}

function required(node: JsxNode, name: string): string {
  const v = attr(node, name);
  if (v === undefined) {
    throw new Error(`<${node.name}> is missing the required "${name}" attribute`);
  }
  return v;
}

/** Sample components accept either `sample_name` or the legacy `sample`. */
function sampleName(node: JsxNode): string {
  const v = attr(node, "sample_name") ?? attr(node, "sample");
  if (v === undefined) {
    throw new Error(`<${node.name}> needs a "sample_name" attribute`);
  }
  return v;
}

/** Fences content, widening the fence if the content itself contains backticks. */
export function fence(content: string, language: string, title?: string): string {
  const longest = [...content.matchAll(/`{3,}/g)].reduce((n, m) => Math.max(n, m[0].length), 0);
  const ticks = "`".repeat(Math.max(3, longest + 1));
  const head = title ? `**${title}**\n\n` : "";
  return `${head}${ticks}${language}\n${content.replace(/\s+$/, "")}\n${ticks}`;
}

/** Escapes the label of a Markdown link so brackets can't break it. */
function linkLabel(text: string): string {
  return text
    .replace(/([[\]])/g, "\\$1")
    .replace(/\s+/g, " ")
    .trim();
}

function apiLink(
  node: JsxNode,
  ctx: EmitContext,
  url: (cls: string, method?: string) => string,
): string {
  const cls = required(node, "class");
  const method = attr(node, "method");
  const simple = cls.split(".").pop() ?? cls;
  const label = attr(node, "text") ?? (method ? `${simple}.${method}` : simple);
  return `[\`${linkLabel(label)}\`](${absolute(ctx.base, url(cls, method))})`;
}

export const renderers: Record<string, Renderer> = {
  // ---------------------------------------------------------------- links --
  async Xref(node, ctx, children) {
    const page = required(node, "page");
    const target = await ctx.resolveXref(page, attrList(node, "section"));
    const text = (await children()).trim() || page;
    return `[${linkLabel(text)}](${absolute(ctx.base, target)})`;
  },

  JavaDocLink: (node, ctx) => apiLink(node, ctx, javadocUrl),
  GroovyDocLink: (node, ctx) => apiLink(node, ctx, groovyDslUrl),
  KotlinDocLink: (node, ctx) => apiLink(node, ctx, kotlinDslUrl),

  // -------------------------------------------------------------- samples --
  SampleScripts(node, ctx) {
    const scripts = ctx.loadScripts({
      sample: sampleName(node),
      basename: required(node, "basename"),
      subpath: attr(node, "subpath"),
      tag: attr(node, "tag"),
    });
    // Both DSLs, adjacent and labelled. Tabs are a rendering affordance and
    // carry no meaning in a flat file, so the label has to become text.
    return scripts
      .map((s) => fence(s.content, s.language === "dcl" ? "kotlin" : s.language, s.filename))
      .join("\n\n");
  },

  SampleFile(node, ctx) {
    const path = required(node, "path");
    const title = attr(node, "title");
    const files = ctx.loadFile({
      sample: sampleName(node),
      path,
      lang: attr(node, "lang"),
      tag: attr(node, "tag"),
    });
    if (files.length === 0) {
      throw new Error(`<SampleFile> resolved no files for "${path}" in "${sampleName(node)}"`);
    }
    return files.map((f) => fence(f.content, f.language, title ?? f.title)).join("\n\n");
  },

  GitHubButton(node, ctx) {
    const sample = attr(node, "sample") ?? attr(node, "sample_name") ?? "";
    const action = attr(node, "action") ?? "view";
    const label = attr(node, "label") ?? (action === "download" ? "Download" : "View on GitHub");
    const sub = attr(node, "subpath");
    const dir = sub ? `${sample}/${sub}` : sample;
    const href = `https://github.com/gradle/gradle/tree/master/platforms/documentation/docs/src/snippets/${dir}`;
    return `[${linkLabel(label)}](${absolute(ctx.base, href)})`;
  },

  // ------------------------------------------------------- content blocks --
  async CalloutList(_node, _ctx, children) {
    // Already a plain ordered list; the markers it pairs with survive inside
    // the preceding fence, so the pairing is preserved by doing nothing to it.
    return (await children()).trim();
  },

  async FileTree(_node, _ctx, children) {
    // Already a nested list. The nesting is the content.
    return (await children()).trim();
  },

  async Collapsible(node, _ctx, children) {
    const summary = attr(node, "summary");
    const body = (await children()).trim();
    // Expanded: hiding content is a rendering concern and an agent should see it.
    return summary ? `**${summary}**\n\n${body}` : body;
  },

  async Task(node, _ctx, children) {
    const name = required(node, "name");
    const dependsOn = attr(node, "dependsOn");
    const body = (await children()).trim();
    const lines = [`### \`${name}\``];
    if (dependsOn) lines.push(`**Depends on:** ${dependsOn}`);
    if (body) lines.push(body);
    return lines.join("\n\n");
  },

  Incubating: () => "**(Incubating)**",

  GradleVersion: (_node, ctx) => ctx.gradleVersion,

  // -------------------------------------------------------- tabs and cards --
  async Tabs(_node, _ctx, children) {
    return (await children()).trim();
  },

  async TabItem(node, _ctx, children) {
    const label = attr(node, "label");
    const body = (await children()).trim();
    // The tab strip disappears in a flat file, so the label becomes a caption.
    return label ? `**${label}**\n\n${body}` : body;
  },

  async Card(node, _ctx, children) {
    const title = attr(node, "title");
    const body = (await children()).trim();
    return title ? `**${title}**\n\n${body}` : body;
  },

  async CardGrid(_node, _ctx, children) {
    return (await children()).trim();
  },

  // --------------------------------------------------------------- media --
  YouTube(node, _ctx) {
    const id = required(node, "id");
    const title = attr(node, "title") ?? "YouTube video";
    return `[${linkLabel(title)}](https://www.youtube.com/watch?v=${id})`;
  },

  Promotion(node, ctx) {
    const href = required(node, "href");
    const title = attr(node, "title") ?? href;
    return `[${linkLabel(title)}](${absolute(ctx.base, href)})`;
  },

  async Image(node, ctx) {
    const alt = attr(node, "alt") ?? "";
    // `src` is normally an imported asset, so the attribute holds a local
    // import name rather than a path. Astro content-hashes and re-encodes
    // images, so only the build knows the servable URL — resolve through the
    // import binding to ask it. A literal `src="…"` is used as-is.
    const binding = importedImageName(node);
    const specifier = binding ? ctx.imports?.get(binding) : undefined;
    const url =
      (specifier ? await ctx.resolveAsset?.(specifier) : undefined) ?? literalAttr(node, "src");
    if (!url) {
      throw new Error(
        `<Image src={${binding ?? "?"}}> could not be resolved to a served URL. ` +
          `A broken image URL is worse than none, so this fails the build.`,
      );
    }
    return `![${linkLabel(alt)}](${absolute(ctx.base, url)})`;
  },

  // ------------------------------------------------- inline HTML passthrough --
  // AsciiDoc definition lists convert to <dl>/<dt>/<dd>; Markdown has no
  // definition list, so a bold term with an indented body is the closest form
  // that keeps the pairing readable.
  async dl(_node, _ctx, children) {
    return (await children()).trim();
  },
  async dt(_node, _ctx, children) {
    return `**${(await children()).trim()}**`;
  },
  async dd(_node, _ctx, children) {
    const body = (await children()).trim();
    return body
      .split("\n")
      .map((l) => (l.trim() ? `  ${l}` : l))
      .join("\n");
  },
  br: () => "  \n",
};

function joinPath(pagePath: string, file: string): string {
  if (file.startsWith("/")) return file;
  return pagePath.replace(/\/+$/, "") + "/" + file.replace(/^\.\//, "");
}

/** `<Image src={deprecations} .../>` → the imported file's basename, if we can see it. */
function importedImageName(node: JsxNode): string | undefined {
  const src = node.attributes?.find((a) => a.type === "mdxJsxAttribute" && a.name === "src");
  if (!src?.value || typeof src.value !== "object") return undefined;
  const raw = String((src.value as { value?: unknown }).value ?? "").trim();
  return raw || undefined;
}

export function hasRenderer(name: string): boolean {
  return Object.prototype.hasOwnProperty.call(renderers, name);
}
