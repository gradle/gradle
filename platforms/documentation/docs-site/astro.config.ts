// @ts-check
import { fileURLToPath } from "node:url";
import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";
import { sidebar } from "./sidebar-structure";
import { rehypeCollectAnchors } from "./plugins/rehype/collect-anchors";
import { remarkSubstituteVariables } from "./plugins/remark/substitute-variables";
import { xrefFallbackReporter } from "./src/lib/xref-fallbacks";
import { variables } from "./src/config/variables";

// https://astro.build/config
export default defineConfig({
  site: "https://docs.gradle.org",
  outDir: "./build/site",
  // Gradle assembles all external resources under build/public: source assets
  // from public/ plus the rendered reference docs (javadoc, kotlin-dsl, dsl)
  // resolved from :docs (see preparePublicDir in build.gradle.kts).
  publicDir: "./build/public",
  vite: {
    resolve: {
      alias: {
        // Cloudflare-docs pattern: swap Starlight's internal sidebar renderer
        // for our copy that adds icons (see src/config/sidebar-icons.ts).
        "./SidebarSublist.astro": fileURLToPath(
          new URL("./src/components/overrides/SidebarSublist.astro", import.meta.url),
        ),
      },
    },
  },
  experimental: {
    contentIntellisense: true,
  },
  markdown: {
    // Resolve `%%name%%` tokens the converter emits for build-time variables
    // (e.g. gradleVersion). Runs before Expressive Code, so it reaches into code
    // fences too. Values are stubbed in src/config/variables.ts for now.
    remarkPlugins: [[remarkSubstituteVariables, variables]],
    rehypePlugins: [rehypeCollectAnchors()],
  },
  integrations: [
    // After `astro build`: fails the build if any <Xref> section fallback is
    // not in the committed baseline (xref-fallbacks-baseline.json).
    // Refresh the baseline with: XREF_UPDATE_BASELINE=1 npm run build
    xrefFallbackReporter(),
    starlight({
      title: "Gradle",
      components: {
        PageTitle: "./src/components/overrides/PageTitle.astro",
        Sidebar: "./src/components/overrides/Sidebar.astro",
      },
      sidebar,
      tableOfContents: {
        minHeadingLevel: 1,
        maxHeadingLevel: 3,
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
      // Expressive Code options live in ec.config.mjs: the callouts plugin is
      // a function, and non-serializable EC options in astro.config break
      // Starlight's <Code> component.
      customCss: [
        "@fontsource/lato/400.css",
        "@fontsource/lato/700.css",
        "./src/styles/custom.css",
      ],
    }),
  ],
});
