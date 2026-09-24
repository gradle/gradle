/**
 * Generates `/llms.txt`, the discovery index described at https://llmstxt.org/.
 *
 * Built from the page metadata rather than from rendered HTML. That is the
 * whole reason this moves out of `documentation-portal`: the portal only ever
 * sees a finished page, so it recovers each title with a regular expression
 * over `<title>` and reconstructs the grouping by scraping a table of contents
 * out of `userguide_single.html`, and it has no descriptions available at all.
 * Here the sidebar supplies the grouping and the frontmatter supplies the
 * description, so every entry can say what the page is for.
 *
 * The site root serves this by redirect to the `current` copy, so it is
 * generated once per version and never composed across versions.
 */
import type { APIRoute } from "astro";
import { getCollection, type CollectionEntry } from "astro:content";
import sidebarStructure from "../../sidebar-structure.json" with { type: "json" };
import { variables } from "#/config/variables";

const SITE = "https://docs.gradle.org";

const TITLE = "Gradle Build Tool Documentation";
const SUMMARY =
  "Official documentation for the Gradle Build Tool, covering project configuration, " +
  "dependency management, task authoring, and plugin development.";

interface Node {
  type: string;
  label: string;
  path?: string;
  link?: string;
  children?: Node[];
}

/** A page entry as it will appear under a heading, in sidebar order. */
interface Item {
  label: string;
  path: string;
}

/**
 * Flattens the sidebar into `heading -> pages`, using each top-level group as
 * the heading and folding nested groups into their parent. Nesting the full
 * tree would produce an index deeper than the format is meant to carry; the
 * top level is the division a reader already navigates by.
 */
function sections(nodes: Node[]): Array<{ heading: string; items: Item[] }> {
  const out: Array<{ heading: string; items: Item[] }> = [];
  const loose: Item[] = [];

  const collect = (node: Node, into: Item[]) => {
    if (node.type === "page" && node.path) into.push({ label: node.label, path: node.path });
    for (const child of node.children ?? []) collect(child, into);
  };

  for (const node of nodes) {
    if (node.type === "group") {
      const items: Item[] = [];
      collect(node, items);
      if (items.length) out.push({ heading: node.label, items });
    } else if (node.type === "page" && node.path) {
      loose.push({ label: node.label, path: node.path });
    }
  }
  if (loose.length) out.unshift({ heading: "Overview", items: loose });
  return out;
}

export const GET: APIRoute = async () => {
  const base = `${SITE}/${variables.gradleVersion}`;
  const docs = await getCollection("docs");

  // Descriptions come from frontmatter; the sidebar only knows structure.
  const byPath = new Map<string, CollectionEntry<"docs">>();
  for (const entry of docs) {
    const id = entry.id.replace(/\/index$/, "").replace(/\.mdx?$/, "");
    byPath.set(id === "index" ? "/" : `/${id}/`, entry);
  }

  const lines: string[] = [`# ${TITLE}`, "", `> ${SUMMARY}`, ""];

  for (const { heading, items } of sections(sidebarStructure as Node[])) {
    const rendered = items
      .map(({ label, path }) => {
        const entry = byPath.get(path);
        if (!entry) return null;
        const description = (entry.data as { description?: string }).description;
        const title = (entry.data as { title?: string }).title ?? label;
        // A bare link is what today's llms.txt emits and what this is meant to
        // replace, so the description is appended whenever the page has one.
        return `- [${title}](${base}${path})${description ? `: ${description}` : ""}`;
      })
      .filter((line): line is string => line !== null);

    if (!rendered.length) continue;
    lines.push(`## ${heading}`, "", ...rendered, "");
  }

  lines.push(
    "## API Reference",
    "",
    `- [Javadoc API Reference](${base}/javadoc/)`,
    `- [Groovy DSL Reference](${base}/dsl/)`,
    `- [Kotlin DSL Reference](${base}/kotlin-dsl/)`,
    "",
  );

  return new Response(lines.join("\n"), {
    headers: { "Content-Type": "text/plain; charset=utf-8" },
  });
};
