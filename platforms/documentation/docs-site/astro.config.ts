// @ts-check
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";
import sidebarStructure from "./sidebar-structure.json" with { type: "json" };

const CONTENT_DOCS_DIR = fileURLToPath(new URL("./src/content/docs/", import.meta.url));

function hasMigratedContent(target: string | null): boolean {
  if (!target) return false;
  const relative = target.replace(/^\/+/, "").replace(/\/+$/, "");
  return existsSync(`${CONTENT_DOCS_DIR}${relative}/index.mdx`);
}

type SidebarStructureNode = SidebarStructureGroup | SidebarStructurePage | SidebarStructureExternal;

interface SidebarStructureGroup {
  type: "group";
  label: string;
  collapsed: boolean;
  children: SidebarStructureNode[];
}

interface SidebarStructurePage {
  type: "page";
  label: string;
  sourceAdoc: string;
  fallbackLink: string;
  target: string | null;
}

interface SidebarStructureExternal {
  type: "external";
  label: string;
  link: string;
}

function toStarlightSidebar(items: SidebarStructureNode[]): Parameters<typeof starlight>[0]["sidebar"] {
  return items.map((item) => {
    if (item.type === "group") {
      return {
        label: item.label,
        collapsed: item.collapsed,
        items: toStarlightSidebar(item.children),
      };
    }

    if (item.type === "page") {
      const migrated = hasMigratedContent(item.target);
      return {
        label: item.label,
        link: migrated ? item.target! : item.fallbackLink,
        badge: migrated ? undefined : { text: "Legacy", variant: "default" as const },
      };
    }

    return {
      label: item.label,
      link: item.link,
    };
  });
}

// https://astro.build/config
export default defineConfig({
  site: "https://docs.gradle.org",
  outDir: "./build/site",
  // We point publicDir into build/ because Gradle assembles all external resources here:
  // source assets from public/ plus the rendered reference docs (javadoc, kotlin-dsl, dsl)
  // resolved from :reference-docs. Keeping it under build/ leaves the source tree intact.
  publicDir: "./build/public",
  experimental: {
    contentIntellisense: true
  },
  integrations: [
    starlight({
      title: "Gradle",
      components: {
        PageTitle: "./src/components/overrides/PageTitle.astro",
      },
      sidebar: toStarlightSidebar(sidebarStructure as SidebarStructureNode[]),
      tableOfContents: {
        minHeadingLevel: 1,
        maxHeadingLevel: 5,
      },
      logo: {
        src: "./src/assets/gradle-logo.svg",
      },
      social: [
        {
          icon: "github",
          label: "GitHub",
          href: "https://github.com/gradle/gradle",
        },
      ],
      expressiveCode: {
        shiki: {
          langAlias: {
            // We consider Declarative Gradle files as Kotlin for syntax highlighting
            dcl: "kotlin",
          },
        },
      },
      customCss: [
        "@fontsource/lato/400.css",
        "@fontsource/lato/700.css",
        "./src/styles/custom.css",
      ],
    }),
  ],
});
