import { describe, expect, it } from "vitest";
import { emitMarkdown, validate, type EmitResult } from "./emit";
import type { EmitContext } from "./context";

const BASE = "https://docs.gradle.org/9.9.0";

function ctx(overrides: Partial<EmitContext> = {}): EmitContext {
  return {
    base: BASE,
    pagePath: "/userguide/reference/demo/",
    gradleVersion: "9.9.0",
    async resolveXref(page, section) {
      const path = `/userguide/reference/${page}/`;
      return section
        ? `${path}#${String(Array.isArray(section) ? section.at(-1) : section)}`
        : path;
    },
    loadScripts: () => [
      { language: "kotlin", label: "Kotlin", filename: "build.gradle.kts", content: "plugins {}" },
      { language: "groovy", label: "Groovy", filename: "build.gradle", content: "plugins {}" },
    ],
    loadFile: () => [{ language: "java", title: "Demo.java", content: "class Demo {}" }],
    ...overrides,
  };
}

const emit = async (body: string, c: EmitContext = ctx()) => (await emitMarkdown(body, c)).markdown;

describe("component renderings", () => {
  it("resolves Xref to an absolute link with its anchor", async () => {
    const md = await emit(`See <Xref page="platforms" section="Platforms">platforms</Xref>.`);
    expect(md.trim()).toBe(`See [platforms](${BASE}/userguide/reference/platforms/#Platforms).`);
  });

  it("uses the page slug when an Xref has no body text", async () => {
    expect(await emit(`<Xref page="platforms"></Xref>`)).toContain("[platforms]");
  });

  it("builds Javadoc, Groovy DSL and Kotlin DSL URLs", async () => {
    const md = await emit(
      `<JavaDocLink class="org.gradle.api.Project" />` +
        `<GroovyDocLink class="org.gradle.api.Project" />` +
        `<KotlinDocLink class="org.gradle.api.Project" />`,
    );
    expect(md).toContain(`${BASE}/javadoc/org/gradle/api/Project.html`);
    expect(md).toContain(`${BASE}/dsl/org.gradle.api.Project.html`);
    expect(md).toContain(`${BASE}/kotlin-dsl/gradle/org.gradle.api/-project/index.html`);
  });

  it("labels a Javadoc link Class.method when a method is given", async () => {
    const md = await emit(`<JavaDocLink class="org.gradle.api.Project" method="getName()" />`);
    expect(md).toContain("[`Project.getName()`]");
    expect(md).toContain("#getName()");
  });

  it("emits one language-tagged fence per DSL for SampleScripts", async () => {
    const md = await emit(`<SampleScripts sample_name="demo" basename="build" />`);
    expect(md).toContain("```kotlin");
    expect(md).toContain("```groovy");
    expect(md).toContain("**build.gradle.kts**");
  });

  it("renders SampleFile with the inferred language and a title", async () => {
    const md = await emit(`<SampleFile sample_name="demo" path="Demo.java" />`);
    expect(md).toContain("**Demo.java**");
    expect(md).toContain("```java\nclass Demo {}\n```");
  });

  it("keeps a CalloutList as a plain ordered list", async () => {
    const md = await emit(`<CalloutList>\n\n1. First.\n2. Second.\n\n</CalloutList>`);
    expect(md).toContain("1. First.");
    expect(md).toContain("2. Second.");
    expect(md).not.toContain("CalloutList");
  });

  it("turns a tab label into a caption, since tabs do not exist in a flat file", async () => {
    const md = await emit(`<Tabs><TabItem label="Kotlin">body text</TabItem></Tabs>`);
    expect(md).toContain("**Kotlin**");
    expect(md).toContain("body text");
  });

  it("expands a Collapsible rather than hiding its body", async () => {
    const md = await emit(`<Collapsible summary="Full output">hidden detail</Collapsible>`);
    expect(md).toContain("**Full output**");
    expect(md).toContain("hidden detail");
  });

  it("renders a definition list as a bold term with an indented body", async () => {
    const md = await emit(`<dl><dt>\`src/main/java\`</dt><dd>Production source.</dd></dl>`);
    expect(md).toContain("**`src/main/java`**");
    expect(md).toContain("Production source.");
  });

  it("renders the remaining simple components", async () => {
    expect(await emit(`<Incubating />`)).toContain("**(Incubating)**");
    expect(await emit(`<GradleVersion />`)).toContain("9.9.0");
    expect(await emit(`<YouTube id="abc123" title="Basics" />`)).toContain(
      "[Basics](https://www.youtube.com/watch?v=abc123)",
    );
  });
});

describe("images", () => {
  it("resolves an imported image to the URL the build actually serves", async () => {
    const body = `import shot from './gradle-basic-1.png';\n\n<Image src={shot} alt="a shot" />`;
    const md = await emit(
      body,
      ctx({
        resolveAsset: (spec) =>
          spec === "./gradle-basic-1.png" ? "/_astro/gradle-basic-1.hash.png" : undefined,
      }),
    );
    expect(md).toContain(`![a shot](${BASE}/_astro/gradle-basic-1.hash.png)`);
  });

  it("fails rather than emitting an image URL it could not resolve", async () => {
    const body = `import shot from './missing.png';\n\n<Image src={shot} alt="x" />`;
    await expect(emit(body, ctx({ resolveAsset: () => undefined }))).rejects.toThrow(
      /could not be resolved to a served URL/,
    );
  });
});

describe("link absolutisation", () => {
  it("absolutises site-root links written as plain Markdown", async () => {
    expect(await emit(`[docs](/userguide/)`)).toContain(`[docs](${BASE}/userguide/)`);
  });

  it("resolves a bare #anchor against the current page", async () => {
    expect(await emit(`[here](#sec:x)`)).toContain(`${BASE}/userguide/reference/demo/#sec:x`);
  });

  it("leaves external links alone", async () => {
    expect(await emit(`[gradle](https://gradle.org)`)).toContain("[gradle](https://gradle.org)");
  });
});

describe("noise removal", () => {
  it("drops component imports", async () => {
    const md = await emit(`import { Xref } from '#/components';\n\nBody.`);
    expect(md).not.toContain("import");
    expect(md.trim()).toBe("Body.");
  });

  it("drops MDX expressions such as converter-gap comments", async () => {
    const md = await emit(`{/* converter-gap: node=sample */}\n\nBody.`);
    expect(md.trim()).toBe("Body.");
  });
});

describe("build gates", () => {
  it("fails on a component with no registered renderer", async () => {
    await expect(emit(`<TotallyNewThing />`)).rejects.toThrow(
      /no Markdown renderer for <TotallyNewThing>/,
    );
  });

  it("tags a bare code fence as text and reports it", async () => {
    const result = await emitMarkdown("```\n> Task :build\n```", ctx());
    expect(result.markdown).toContain("```text");
    expect(result.untaggedFences).toHaveLength(1);
  });

  it("does not mistake JSX quoted inside a code span for surviving JSX", async () => {
    const result = await emitMarkdown('Use `<Xref page="x">` in MDX.', ctx());
    expect(validate("/p/", result)).toEqual([]);
  });

  it("flags a page over the hard size limit", () => {
    const result: EmitResult = {
      markdown: "x".repeat(200 * 1024),
      untaggedFences: [],
      survivingJsx: [],
    };
    expect(validate("/p/", result).map((p) => p.kind)).toContain("too-large");
  });

  it("flags a relative link that escaped absolutisation", () => {
    const result: EmitResult = {
      markdown: "[x](../elsewhere/)\n",
      untaggedFences: [],
      survivingJsx: [],
    };
    expect(validate("/p/", result).map((p) => p.kind)).toContain("relative-link");
  });
});
