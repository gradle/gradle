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
  // Every build is published under a version prefix (docs.gradle.org/9.9.0/,
  // and the same bytes again under /current/). Starlight derives the canonical
  // link, og:url and the sitemap from `site` + `base`, so without the prefix
  // here all three point at URLs that do not exist. Internal links are
  // unaffected: relativeLinks() rewrites them to page-relative below.
  // The trailing slash matters for the dev and preview servers: it is what they
  // print as the address to open, and with it a request that omits the slash
  // fails outright instead of serving a page whose relative assets all resolve
  // one directory too high. Published, the CDN redirects that form anyway.
  base: `/${variables.gradleVersion}/`,
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
    // in the build output. The dev server serves under `base` too, so a link
    // that works there works published.
    relativeLinks(),
    // After `astro build`: fails the build if any <Xref> section fallback is
    // not in the committed baseline (xref-fallbacks-baseline.json).
    // Refresh the baseline with: XREF_UPDATE_BASELINE=1 npm run build
    xrefFallbackReporter(),
    starlight({
      title: "Gradle",
      // Advertise the per-page Markdown sibling emitted by
      // src/pages/[...slug]/index.md.ts. Emitting the file is not enough —
      // agents have to be able to find it, and this is the mechanism they
      // look for. The href is page-relative and identical on every page
      // because build.format: 'directory' puts index.md beside index.html.
      head: [
        {
          tag: "link",
          attrs: {
            rel: "alternate",
            type: "text/markdown",
            href: "./index.md",
          },
        },
      ],
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
