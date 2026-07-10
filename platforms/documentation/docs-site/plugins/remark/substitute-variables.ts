// Substitutes `%%name%%` tokens with build-time values, in prose and code alike.
// In an MDX page:
//
//     ```bash
//     $ unzip -d /opt/gradle gradle-%%gradleVersion%%-bin.zip
//     ```
//
// ships as `$ unzip -d /opt/gradle gradle-9.7.0-bin.zip` (whatever version the
// build was given).
//
// To show a token literally instead, opt the fence out with
// ```bash no-substitute — that block is left untouched. In hand-written
// prose, skip tokens altogether and use a JSX expression (import the value
// from src/config/variables.ts); prose substitution exists only because the
// converter emits tokens wherever the AsciiDoc source referenced the
// attribute.
//
// Runs in the remark phase, which is before Expressive Code (a rehype plugin),
// so substitution inside fenced code blocks lands before highlighting — no
// highlight-span splitting. The converter injects these tokens for variables
// it defers to build time (e.g. gradleVersion); values come from
// src/config/variables.ts, passed in from astro.config.ts.
//
// An unknown token fails the build rather than shipping a literal `%%…%%`,
// catching typos and converter/config drift.
const TOKEN = /%%([A-Za-z][A-Za-z0-9]*)%%/g;

export function remarkSubstituteVariables(values: Record<string, string> = {}) {
  function substitute(text: string): string {
    return text.replace(TOKEN, (match, name) => {
      if (Object.prototype.hasOwnProperty.call(values, name)) {
        return values[name];
      }
      throw new Error(
        `remark-substitute-variables: unknown variable token "${match}". ` +
          `Add "${name}" to src/config/variables.ts or fix the reference.`,
      );
    });
  }

  // Escape hatch: a fenced block marked ```lang no-substitute ships its
  // `%%…%%` tokens literally (e.g. when documenting this mechanism).
  function optsOut(node: any): boolean {
    return (
      node.type === "code" &&
      typeof node.meta === "string" &&
      node.meta.split(/\s+/).includes("no-substitute")
    );
  }

  function walk(node: any): void {
    if (
      typeof node.value === "string" &&
      (node.type === "text" ||
        node.type === "code" ||
        node.type === "inlineCode" ||
        node.type === "html") &&
      !optsOut(node)
    ) {
      node.value = substitute(node.value);
    }
    if ((node.type === "link" || node.type === "image") && typeof node.url === "string") {
      node.url = substitute(node.url);
    }
    if (Array.isArray(node.children)) {
      for (const child of node.children) walk(child);
    }
  }

  return (tree: any): void => {
    walk(tree);
  };
}
