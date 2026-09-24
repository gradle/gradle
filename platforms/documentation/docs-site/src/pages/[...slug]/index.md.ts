/**
 * Emits the per-page Markdown sibling: `/<page>/index.md` next to
 * `/<page>/index.html`.
 *
 * This is a route rather than an `astro:build:done` integration on purpose.
 * Resolving `<Xref section>` needs each target page's anchor table, which
 * `render(entry).remarkPluginFrontmatter` only exposes inside the Astro
 * runtime — the same source `Xref.astro` reads, so the `.md` link and the HTML
 * link can never disagree.
 */
import type { APIRoute, GetStaticPaths } from "astro";
import { getCollection, render, type CollectionEntry } from "astro:content";
import { getImage } from "astro:assets";
import Slugger from "github-slugger";
import { emitMarkdown, validate, SIZE_WARN_BYTES, type PageProblem } from "#/lib/markdown/emit";
import type { EmitContext } from "#/lib/markdown/context";
import { getPageScopedSamplePath, loadSampleScripts } from "#/lib/samples";
import { selectSampleContent } from "#/lib/sample-tags";
import { variables } from "#/config/variables";

const SITE = "https://docs.gradle.org";

// Eager-globbing the content images gives us the ImageMetadata that `<Image>`
// receives, which `getImage()` then turns into the optimized asset.
const contentImages = import.meta.glob<{ default: ImageMetadata }>(
  "/src/content/docs/**/*.{png,jpg,jpeg,webp,avif,gif,svg}",
  { eager: true },
);

const sampleFiles = import.meta.glob("/src/samples/**/*", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

/** Mirrors SampleFile.astro's extension → language table. */
function inferLanguage(filename: string): string {
  if (filename.endsWith(".gradle.kts") || filename.endsWith(".kt")) return "kotlin";
  if (filename.endsWith(".gradle") || filename.endsWith(".groovy")) return "groovy";
  if (filename.endsWith(".java")) return "java";
  if (filename.endsWith(".xml")) return "xml";
  if (filename.endsWith(".properties")) return "properties";
  if (filename.endsWith(".toml")) return "toml";
  if (filename.endsWith(".yaml") || filename.endsWith(".yml")) return "yaml";
  if (filename.endsWith(".json")) return "json";
  if (filename.endsWith(".sh")) return "bash";
  return "text";
}

const SAMPLE_FILE_VARIANTS = [
  { key: "kotlin", srcExt: "kt" },
  { key: "groovy", srcExt: "groovy" },
  { key: "dcl", srcExt: "dcl" },
] as const;

function slugOf(text: string): string {
  return new Slugger().slug(text);
}

/** Entry id → site-root-relative page path. The root page is `/`, not `/index/`. */
function pathOf(entry: CollectionEntry<"docs">): string {
  const id = entry.id.replace(/\/index$/, "").replace(/\.mdx?$/, "");
  return id === "index" || id === "" ? "/" : `/${id}/`;
}

/** Page slug (the trailing path segment) → entry, matching Xref.astro. */
function indexBySlug(docs: CollectionEntry<"docs">[]): Map<string, CollectionEntry<"docs">[]> {
  const map = new Map<string, CollectionEntry<"docs">[]>();
  for (const entry of docs) {
    const slug = entry.id
      .replace(/\/index$/, "")
      .replace(/\.mdx?$/, "")
      .split("/")
      .pop()!;
    map.set(slug, [...(map.get(slug) ?? []), entry]);
  }
  return map;
}

export const getStaticPaths: GetStaticPaths = async () => {
  const docs = await getCollection("docs");
  return docs.map((entry) => {
    const id = entry.id.replace(/\/index$/, "").replace(/\.mdx?$/, "");
    // The root page must emit /index.md, not /index/index.md.
    return { params: { slug: id === "index" || id === "" ? undefined : id }, props: { entry } };
  });
};

export const GET: APIRoute = async ({ props }) => {
  const entry = (props as { entry: CollectionEntry<"docs"> }).entry;
  const docs = await getCollection("docs");
  const bySlug = indexBySlug(docs);
  const pagePath = pathOf(entry);
  const base = `${SITE}/${variables.gradleVersion}`;

  const anchorCache = new Map<string, { slug: string; path: string[] }[]>();
  async function anchorsFor(target: CollectionEntry<"docs">) {
    const cached = anchorCache.get(target.id);
    if (cached) return cached;
    const { remarkPluginFrontmatter } = await render(target);
    const anchors =
      (remarkPluginFrontmatter as { anchors?: { slug: string; path: string[] }[] }).anchors ?? [];
    anchorCache.set(target.id, anchors);
    return anchors;
  }

  const ctx: EmitContext = {
    base,
    pagePath,
    gradleVersion: variables.gradleVersion,

    async resolveXref(page, section) {
      const matches = bySlug.get(page) ?? [];
      if (matches.length !== 1) {
        throw new Error(
          `Xref: ${matches.length === 0 ? "page not found" : "ambiguous page slug"}: ${page}`,
        );
      }
      const target = matches[0];
      const targetPath = pathOf(target);
      if (!section) return targetPath;

      const title = (target.data as { title?: string }).title;
      const lookup = Array.isArray(section) ? section : [section];
      const isPageTitle =
        title != null &&
        lookup.length === 1 &&
        (lookup[0] === title || slugOf(lookup[0]) === slugOf(title));
      if (isPageTitle) return targetPath;

      const anchors = await anchorsFor(target);
      const equiv = (a: string, b: string) => a === b || slugOf(a) === slugOf(b);
      const candidates =
        lookup.length === 1
          ? anchors.filter((a) => equiv(a.path[a.path.length - 1], lookup[0]))
          : anchors.filter(
              (a) =>
                a.path.length >= lookup.length &&
                a.path.slice(-lookup.length).every((t, i) => equiv(t, lookup[i])),
            );

      // Same deliberate degradation as Xref.astro: an unresolved section
      // becomes a page link rather than failing the page. The HTML build's
      // xref baseline is what keeps new fallbacks from creeping in.
      return candidates.length === 1 ? `${targetPath}#${candidates[0].slug}` : targetPath;
    },

    async resolveAsset(specifier) {
      // Specifiers are page-relative (`./gradle-basic-1.png`).
      const dir = `/src/content/docs${pagePath}`.replace(/\/+$/, "");
      const key = `${dir}/${specifier.replace(/^\.\//, "")}`;
      const metadata = contentImages[key]?.default;
      if (!metadata) return undefined;
      // Go through the image service rather than using metadata.src: that is
      // the unprocessed original, and referencing it makes Astro emit a second
      // copy of the file that nothing else asks for. getImage() yields the
      // same asset <Image> puts in the HTML.
      return (await getImage({ src: metadata })).src;
    },

    loadScripts({ sample, basename, subpath, tag }) {
      const scoped = getPageScopedSamplePath(pagePath, sample);
      return loadSampleScripts(scoped, basename, subpath ?? "", tag).scripts;
    },

    loadFile({ sample, path, lang, tag }) {
      const scoped = getPageScopedSamplePath(pagePath, sample);
      const isTemplate = path.includes("{lang}") || path.includes("{srcExt}");
      const candidates = isTemplate
        ? SAMPLE_FILE_VARIANTS.map((v) =>
            path.replaceAll("{lang}", v.key).replaceAll("{srcExt}", v.srcExt),
          )
        : [path];

      const out: Array<{ language: string; title: string; content: string }> = [];
      for (const resolved of candidates) {
        const filePath = `/src/samples/${scoped}/${resolved}`;
        const content = sampleFiles[filePath];
        if (content === undefined || content.trim().length === 0) continue;
        out.push({
          language: lang ?? inferLanguage(resolved),
          title: resolved,
          content: selectSampleContent(content, tag, filePath),
        });
      }
      return out;
    },
  };

  const result = await emitMarkdown(entry.body ?? "", ctx, pagePath);

  // Starlight renders the title and description from frontmatter, so the body
  // never contains them. Without this the .md would arrive untitled.
  const meta = entry.data as { title?: string; description?: string };
  const heading = meta.title ? `# ${meta.title}\n\n` : "";
  const intro = meta.description ? `${meta.description}\n\n` : "";
  result.markdown = heading + intro + result.markdown;
  const problems: PageProblem[] = validate(pagePath, result);

  if (problems.length > 0) {
    throw new Error(
      `Markdown emit failed for ${pagePath}:\n` +
        problems.map((p) => `  [${p.kind}] ${p.detail}`).join("\n"),
    );
  }

  const bytes = Buffer.byteLength(result.markdown, "utf8");
  if (bytes > SIZE_WARN_BYTES) {
    console.warn(
      `[markdown-emit] ${pagePath} is ${Math.round(bytes / 1024)} KB — over the ` +
        `${SIZE_WARN_BYTES / 1024} KB soft budget for a single agent fetch.`,
    );
  }

  return new Response(result.markdown, {
    headers: { "Content-Type": "text/markdown; charset=utf-8" },
  });
};
