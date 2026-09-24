import { describe, expect, it } from "vitest";
import { remarkSubstituteVariables } from "./substitute-variables";

const values = { gradleVersion: "9.7.0" };

/** Runs the plugin against a mdast tree in place and returns it. */
function run(tree: any, vars: Record<string, string> = values): any {
  remarkSubstituteVariables(vars)(tree);
  return tree;
}

describe("remarkSubstituteVariables", () => {
  it("substitutes inside fenced code and inline code (the places JSX can't go)", () => {
    const tree = {
      type: "root",
      children: [
        { type: "code", lang: "bash", value: "unzip gradle-%%gradleVersion%%-bin.zip" },
        { type: "inlineCode", value: "--gradle-version %%gradleVersion%%" },
      ],
    };
    run(tree);
    expect(tree.children[0].value).toBe("unzip gradle-9.7.0-bin.zip");
    expect(tree.children[1].value).toBe("--gradle-version 9.7.0");
  });

  it("replaces every occurrence in a code block", () => {
    const tree = {
      type: "root",
      children: [{ type: "code", lang: "bash", value: "%%gradleVersion%% then %%gradleVersion%%" }],
    };
    run(tree);
    expect(tree.children[0].value).toBe("9.7.0 then 9.7.0");
  });

  it("recurses into nested children", () => {
    const tree = {
      type: "root",
      children: [
        {
          type: "paragraph",
          children: [
            { type: "emphasis", children: [{ type: "inlineCode", value: "v%%gradleVersion%%" }] },
          ],
        },
      ],
    };
    run(tree);
    expect(tree.children[0].children[0].children[0].value).toBe("v9.7.0");
  });

  it("leaves a no-substitute fence untouched, unknown tokens included", () => {
    const tree = {
      type: "root",
      children: [
        {
          type: "code",
          lang: "bash",
          meta: "no-substitute",
          value: "literal %%gradleVersion%% and %%notAVariable%%",
        },
      ],
    };
    run(tree);
    expect(tree.children[0].value).toBe("literal %%gradleVersion%% and %%notAVariable%%");
  });

  it("substitutes in prose text nodes (the converter emits tokens in sentences too)", () => {
    const tree = {
      type: "root",
      children: [{ type: "text", value: "Gradle %%gradleVersion%% rocks" }],
    };
    run(tree);
    expect(tree.children[0].value).toBe("Gradle 9.7.0 rocks");
  });

  it("rejects an unknown token in prose (typo guard)", () => {
    const tree = {
      type: "root",
      children: [{ type: "text", value: "Gradle %%notAVariable%% rocks" }],
    };
    expect(() => run(tree)).toThrow(/unknown variable token/);
  });

  it("rejects a token in a link url", () => {
    const tree = {
      type: "root",
      children: [
        {
          type: "link",
          url: "https://services.gradle.org/distributions/gradle-%%gradleVersion%%-bin.zip",
          children: [],
        },
      ],
    };
    expect(() => run(tree)).toThrow(/link URL/);
  });

  it("leaves content without tokens untouched", () => {
    const tree = { type: "root", children: [{ type: "text", value: "nothing to see" }] };
    run(tree);
    expect(tree.children[0].value).toBe("nothing to see");
  });

  it("does not match a bare %% without a closing pair (corpus regression: my%%badly%encoded)", () => {
    const tree = { type: "root", children: [{ type: "text", value: "my%%badly%encoded%path" }] };
    run(tree);
    expect(tree.children[0].value).toBe("my%%badly%encoded%path");
  });

  it("fails fast on an unknown token in code", () => {
    const tree = { type: "root", children: [{ type: "inlineCode", value: "%%bogusVariable%%" }] };
    expect(() => run(tree)).toThrow(/unknown variable token "%%bogusVariable%%"/);
  });
});
