import { describe, expect, it } from "vitest";
import { remarkSubstituteVariables } from "./substitute-variables";

const values = { gradleVersion: "9.7.0" };

/** Runs the plugin against a mdast tree in place and returns it. */
function run(tree: any, vars: Record<string, string> = values): any {
  remarkSubstituteVariables(vars)(tree);
  return tree;
}

describe("remarkSubstituteVariables", () => {
  it("substitutes a token in a prose text node", () => {
    const tree = {
      type: "root",
      children: [{ type: "text", value: "Gradle %%gradleVersion%% rocks" }],
    };
    run(tree);
    expect(tree.children[0].value).toBe("Gradle 9.7.0 rocks");
  });

  it("substitutes inside fenced code and inline code (the reason a component can't)", () => {
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

  it("substitutes inside a link url", () => {
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
    run(tree);
    expect(tree.children[0].url).toBe(
      "https://services.gradle.org/distributions/gradle-9.7.0-bin.zip",
    );
  });

  it("replaces every occurrence in a string", () => {
    const tree = {
      type: "root",
      children: [{ type: "text", value: "%%gradleVersion%% then %%gradleVersion%%" }],
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
            { type: "emphasis", children: [{ type: "text", value: "v%%gradleVersion%%" }] },
          ],
        },
      ],
    };
    run(tree);
    expect(tree.children[0].children[0].children[0].value).toBe("v9.7.0");
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

  it("fails fast on an unknown token", () => {
    const tree = { type: "root", children: [{ type: "text", value: "%%bogusVariable%%" }] };
    expect(() => run(tree)).toThrow(/unknown variable token "%%bogusVariable%%"/);
  });
});
