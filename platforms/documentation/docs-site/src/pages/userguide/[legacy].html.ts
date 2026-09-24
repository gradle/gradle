/**
 * Emits a redirect stub for every legacy user-manual URL.
 *
 * Starlight gives each page its own directory, so `userguide/installation.html`
 * becomes `userguide/releases/installation/`. Three things depend on the old
 * URL continuing to resolve: inbound links, search rankings, and the canonical
 * tag baked into every archived version, which points at
 * `/current/userguide/<page>.html`. That last one makes these stubs a
 * prerequisite for cutover rather than an SEO clean-up — without them the
 * canonical on thousands of immutable archived pages dead-ends at a 404.
 *
 * Stubs are files inside one version's build, which is why they are generated
 * here rather than configured as Cloudflare rules. A rule matching
 * `/*\/userguide/installation.html` would hijack 8.5's genuine page; a file can
 * only ever affect the version it shipped in. They also cost nothing at the
 * edge and ship inside the offline distribution.
 *
 * Fragments are never sent to a server, so the anchor translation has to run in
 * the browser. Each stub embeds only its own page's table.
 */
import type { APIRoute, GetStaticPaths } from "astro";
import { getCollection, render, type CollectionEntry } from "astro:content";
import Slugger from "github-slugger";
import sidebarStructure from "../../../sidebar-structure.json" with { type: "json" };
import anchorMap from "../../../anchor-map.json" with { type: "json" };
import { variables } from "#/config/variables";

const SITE = "https://docs.gradle.org";

interface Node {
  type: string;
  path?: string;
  sourceAdoc?: string;
  children?: Node[];
}

/** Legacy `.html` basename → the new page path, from the sidebar. */
function legacyMap(): Map<string, string> {
  const map = new Map<string, string>();
  const walk = (node: Node) => {
    if (node.type === "page" && node.path && node.sourceAdoc) {
      const base = node.sourceAdoc
        .split("/")
        .pop()!
        .replace(/\.adoc$/, "");
      map.set(base, node.path);
    }
    for (const child of node.children ?? []) walk(child);
  };
  for (const node of sidebarStructure as Node[]) walk(node);
  return map;
}

export const getStaticPaths: GetStaticPaths = async () => {
  const docs = await getCollection("docs");
  const byPath = new Map<string, CollectionEntry<"docs">>();
  for (const entry of docs) {
    const id = entry.id.replace(/\/index$/, "").replace(/\.mdx?$/, "");
    byPath.set(id === "index" ? "/" : `/${id}/`, entry);
  }

  return [...legacyMap()]
    .filter(([, path]) => byPath.has(path))
    .map(([legacy, path]) => ({
      params: { legacy },
      props: { target: path, entry: byPath.get(path)! },
    }));
};

export const GET: APIRoute = async ({ props }) => {
  const { target, entry } = props as { target: string; entry: CollectionEntry<"docs"> };
  const absolute = `${SITE}/${variables.gradleVersion}${target}`;

  // heading text -> slug, from the same anchor table <Xref> resolves against.
  const { remarkPluginFrontmatter } = await render(entry);
  const anchors =
    (remarkPluginFrontmatter as { anchors?: { slug: string; path: string[] }[] }).anchors ?? [];
  const slugOf = (text: string) => new Slugger().slug(text);
  const byHeading = new Map<string, string>();
  for (const anchor of anchors) {
    const leaf = anchor.path[anchor.path.length - 1];
    if (leaf && !byHeading.has(leaf)) byHeading.set(leaf, anchor.slug);
  }

  // legacy anchor id -> new slug, by way of the heading text the converter recorded.
  const legacyAnchors = (anchorMap as Record<string, Record<string, string>>)[target] ?? {};
  const table: Record<string, string> = {};
  for (const [legacyId, heading] of Object.entries(legacyAnchors)) {
    const slug = byHeading.get(heading) ?? findBySlugEquivalence(byHeading, heading, slugOf);
    if (slug) table[legacyId] = slug;
  }

  const title = (entry.data as { title?: string }).title ?? target;
  // The stub sits at <version>/userguide/<legacy>.html and the built tree is
  // served under a version prefix we do not control, so the redirect target
  // must be page-relative. A root-relative "/userguide/..." would escape the
  // version directory and land on the site root. The relativeLinks integration
  // rewrites anchors in the body but cannot reach a meta tag or a JS string.
  const relative = target.startsWith("/userguide/")
    ? target.slice("/userguide/".length) || "./"
    : target;
  const html = stub(relative, absolute, title, table);
  return new Response(html, { headers: { "Content-Type": "text/html; charset=utf-8" } });
};

/**
 * The converter records heading text from the raw AsciiDoc while the anchor
 * table holds post-parse text, so the two can differ in markup while slugging
 * identically. This is the same fallback `Xref.astro` applies.
 */
function findBySlugEquivalence(
  byHeading: Map<string, string>,
  heading: string,
  slugOf: (t: string) => string,
): string | undefined {
  const wanted = slugOf(heading);
  for (const [text, slug] of byHeading) {
    if (slugOf(text) === wanted) return slug;
  }
  return undefined;
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;");
}

function stub(
  /** Page-relative, resolved against the stub's own directory. */
  target: string,
  absolute: string,
  title: string,
  table: Record<string, string>,
): string {
  const json = JSON.stringify(table);
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>${escapeHtml(title)}</title>
<link rel="canonical" href="${escapeHtml(absolute)}">
<meta name="robots" content="noindex">
<meta http-equiv="refresh" content="0; url=${escapeHtml(target)}">
<script>
// Translate the legacy AsciiDoc anchor before the meta refresh fires. AsciiDoc
// anchor ids are unrelated to the ids Starlight derives from heading text, so
// without this a deep link lands on the right page at the wrong position.
// An unknown anchor falls through to the top of the page.
(function () {
  var anchors = ${json};
  var hash = location.hash.replace(/^#/, "");
  var slug = Object.prototype.hasOwnProperty.call(anchors, hash) ? anchors[hash] : null;
  location.replace(${JSON.stringify(target)} + (slug ? "#" + slug : ""));
})();
</script>
</head>
<body>
<p>This page has moved to <a href="${escapeHtml(target)}">${escapeHtml(title)}</a>.</p>
</body>
</html>
`;
}
