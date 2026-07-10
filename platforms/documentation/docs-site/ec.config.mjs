// @ts-check
/**
 * Expressive Code configuration. Lives in its own file (not astro.config.ts)
 * because plugin functions are not JSON-serializable, and Starlight's <Code>
 * component requires serializable EC options in the Astro config — the same
 * reason cloudflare-docs uses ec.config.mjs.
 */
import { defineEcConfig } from "astro-expressive-code";
import { pluginCallouts } from "./plugins/expressive-code/callouts.mjs";

export default defineEcConfig({
  // AsciiDoc-style callout bubbles in code blocks (pairs with <CalloutList>)
  plugins: [pluginCallouts()],
  // Code-frame look borrowed from cloudflare-docs (ec.config.mjs):
  // thin 1px border, tight 0.25rem radius, flat (no drop shadow), and
  // their Phosphor "Copy" icon for the copy button.
  styleOverrides: {
    borderWidth: "1px",
    borderRadius: "0.25rem",
    frames: {
      frameBoxShadowCssValue: "none",
      copyIcon: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 256 256' fill='black'%3E%3Cpath d='M216,32H88a8,8,0,0,0-8,8V80H40a8,8,0,0,0-8,8V216a8,8,0,0,0,8,8H168a8,8,0,0,0,8-8V176h40a8,8,0,0,0,8-8V40A8,8,0,0,0,216,32ZM160,208H48V96H160Zm48-48H176V88a8,8,0,0,0-8-8H96V48H208Z'/%3E%3C/svg%3E")`,
    },
  },
  shiki: {
    langAlias: {
      // We consider Declarative Gradle files as Kotlin for syntax highlighting
      dcl: "kotlin",
    },
  },
});
