// @ts-check
import { fileURLToPath } from "node:url";
import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";
import relativeLinks from "astro-relative-links";
import { sidebar } from "./sidebar-structure";
import { rehypeCollectAnchors } from "./plugins/rehype/collect-anchors";
import { remarkSubstituteVariables } from "./plugins/remark/substitute-variables";
import { xrefFallbackReporter } from "./src/lib/xref-fallbacks";
import { variables } from "./src/config/variables";

// https://astro.build/config
export default defineConfig({
  site: "https://docs.gradle.org",
  // When the Gradle build (:docs-site in gradle/gradle) drives Astro, it
  // assembles the public assets (rendered reference docs + public/) and expects
  // the site output under its build/ directory; it supplies both dirs via env.
  // Local dev in this repo falls back to Astro's defaults (public/, dist/).
  outDir: process.env.ASTRO_OUT_DIR,
  publicDir: process.env.ASTRO_PUBLIC_DIR,
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
    // The built tree is served under a version prefix we don't control
    // (docs.gradle.org/current/, /9.7.0/, ...), so absolute internal URLs
    // would escape the version dir. This rewrites them all to page-relative
    // in the build output; dev is unaffected (served at root).
    relativeLinks(),
    // After `astro build`: fails the build if any <Xref> section fallback is
    // not in the committed baseline (xref-fallbacks-baseline.json).
    // Refresh the baseline with: XREF_UPDATE_BASELINE=1 npm run build
    xrefFallbackReporter(),
    starlight({
      title: "Gradle",
      components: {
        PageTitle: "./src/components/overrides/PageTitle.astro",
        // Adds the docs.gradle.org site menu to the header, CF-docs style
        Header: "./src/components/overrides/Header.astro",
        Sidebar: "./src/components/overrides/Sidebar.astro",
      },
      sidebar,
      tableOfContents: {
        minHeadingLevel: 1,
        maxHeadingLevel: 3,
      },
      logo: {
        // Landscape brand logos (include the wordmark, hence replacesTitle);
        // separate variants per color scheme.
        light: "./src/assets/gradle-logo-light.svg",
        dark: "./src/assets/gradle-logo-dark.svg",
        replacesTitle: true,
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
