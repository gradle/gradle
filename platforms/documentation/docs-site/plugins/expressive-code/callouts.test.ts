import { describe, expect, it } from "vitest";
import { matchCalloutComment } from "./callouts.mjs";

describe("matchCalloutComment", () => {
  it("matches the converter shape: // (1)", () => {
    const m = matchCalloutComment("class Example {        // (1)");
    expect(m).toEqual({ commentStart: 23, tokens: [1] });
  });

  it("matches raw AsciiDoc conums: // <1>", () => {
    expect(matchCalloutComment("void run() {}  // <2>")?.tokens).toEqual([2]);
  });

  it("matches hash comments and xml comments", () => {
    expect(matchCalloutComment("key=value # (3)")?.tokens).toEqual([3]);
    expect(matchCalloutComment("<bean/> <!-- (4) -->")?.tokens).toEqual([4]);
  });

  it("collects multiple markers in one comment", () => {
    expect(matchCalloutComment("foo() // (1) (2)")?.tokens).toEqual([1, 2]);
  });

  it("matches a marker-only line", () => {
    expect(matchCalloutComment("// (5)")?.tokens).toEqual([5]);
  });

  it("matches XML conums: <!--1-->", () => {
    expect(matchCalloutComment('<ant dir="x/util" target="build"/>  <!--1-->')?.tokens).toEqual([
      1,
    ]);
    expect(matchCalloutComment("<project/> <!--.-->")?.tokens).toEqual(["."]);
    // A real XML tag at end of line must not match.
    expect(matchCalloutComment('<target name="build">')).toBeNull();
  });

  it("matches auto-numbered conums as dot tokens", () => {
    expect(matchCalloutComment('create<AggregateTestReport>("x") { // <.>')?.tokens).toEqual(["."]);
    expect(matchCalloutComment("foo() // <.> <.>")?.tokens).toEqual([".", "."]);
    expect(matchCalloutComment("├── build.gradle.kts <.>", true)?.tokens).toEqual(["."]);
  });

  it("never matches without a comment leader", () => {
    expect(matchCalloutComment("foo(1)")).toBeNull();
    expect(matchCalloutComment("bar[0] = baz(2)")).toBeNull();
  });

  it("matches bare markers only when allowBare is set (callouts meta flag)", () => {
    expect(matchCalloutComment("├── include-plugin-build <1>")).toBeNull();
    expect(matchCalloutComment("├── include-plugin-build <1>", true)?.tokens).toEqual([1]);
    expect(matchCalloutComment("└── url-verifier-plugin (2)", true)?.tokens).toEqual([2]);
    // Still requires the marker to end the line, and whitespace before it.
    expect(matchCalloutComment("foo(1)", true)).toBeNull();
    expect(matchCalloutComment("bar (1) baz", true)).toBeNull();
  });

  it("never matches when the comment has other text", () => {
    expect(matchCalloutComment("foo() // returns (1) on success")).toBeNull();
  });

  it("never matches mid-line markers", () => {
    expect(matchCalloutComment("foo() // (1) then more code")).toBeNull();
  });
});
