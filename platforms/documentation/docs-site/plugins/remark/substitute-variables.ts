// Substitutes `%%name%%` tokens with build-time values, inside fenced code
// blocks and inline code only. In an MDX page:
//
//     ```bash
//     $ unzip -d /opt/gradle gradle-%%gradleVersion%%-bin.zip
//     ```
//
// ships as `$ unzip -d /opt/gradle gradle-9.7.0-bin.zip` (whatever version the
// build was given).
//
// Code is the one place a JSX expression can't go, so tokens are scoped to it.
// In prose, import the value from src/config/variables.ts and use a JSX
// expression (a token in prose fails the build). To show a token literally
// inside a code block, opt the fence out with ```bash no-substitute.
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

  // Tokens deliberately do not substitute outside code: in prose, a JSX
  // expression is the right tool. Fail loudly so a stray token can't ship.
  function rejectTokens(value: string, where: string): void {
    const found = value.match(TOKEN);
    if (found) {
      throw new Error(
        `remark-substitute-variables: token "${found[0]}" in ${where} ` +
          `("${value.trim().slice(0, 60)}"). Tokens only substitute inside ` +
          `code blocks and inline code; import the value from ` +
          `src/config/variables.ts and use a JSX expression instead.`,
      );
    }
  }

  function walk(node: any): void {
    if (node.type === "code" || node.type === "inlineCode") {
      if (typeof node.value === "string" && !optsOut(node)) {
        node.value = substitute(node.value);
      }
    } else if (
      typeof node.value === "string" &&
      (node.type === "text" || node.type === "html")
    ) {
      rejectTokens(node.value, "prose");
    } else if (
      (node.type === "link" || node.type === "image") &&
      typeof node.url === "string"
    ) {
      rejectTokens(node.url, `a ${node.type} URL`);
    }
    if (Array.isArray(node.children)) {
      for (const child of node.children) walk(child);
    }
  }

  return (tree: any): void => {
    walk(tree);
  };
}
