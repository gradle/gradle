import { describe, expect, it } from "vitest";
import {
  reduceVersions,
  renderVersionMenu,
  versionDocsUrl,
  type VersionsIndex,
} from "./version-menu";

function entry(
  version: string,
  flags: { final?: boolean; current?: boolean; activeRc?: boolean; nightly?: boolean } = {},
) {
  return {
    version,
    final: flags.final ?? true,
    current: flags.current ?? false,
    activeRc: flags.activeRc ?? false,
    nightly: flags.nightly ?? false,
  };
}

describe("reduceVersions", () => {
  it("keeps the latest final per minor even when the payload is ordered by build time", () => {
    // services.gradle.org orders by build time: 8.14.5 shipped between 9.5.x releases.
    const raw = [
      entry("9.6.1", { current: true }),
      entry("9.6.0"),
      entry("9.5.1"),
      entry("8.14.5"),
      entry("9.5.0"),
      entry("8.14.4"),
    ];
    expect(reduceVersions(raw)).toEqual({
      current: "9.6.1",
      versions: ["9.6.1", "9.5.1", "8.14.5"],
      activeRc: null,
      nightly: null,
    });
  });

  it("extracts the active RC and latest nightly, ignoring non-final entries otherwise", () => {
    const raw = [
      entry("9.7.0-20260706133305+0000", { final: false, nightly: true }),
      entry("9.7.0-rc-1", { final: false, activeRc: true }),
      entry("9.6.1", { current: true }),
    ];
    const index = reduceVersions(raw);
    expect(index.versions).toEqual(["9.6.1"]);
    expect(index.activeRc).toBe("9.7.0-rc-1");
    expect(index.nightly).toBe("9.7.0-20260706133305+0000");
  });

  it("handles two-segment versions from the 0.x era", () => {
    const raw = [entry("9.6.1", { current: true }), entry("0.9"), entry("0.9.2")];
    expect(reduceVersions(raw).versions).toEqual(["9.6.1", "0.9.2"]);
  });

  it("falls back to the newest final when no entry is flagged current", () => {
    expect(reduceVersions([entry("8.5"), entry("9.0.0")]).current).toBe("9.0.0");
  });

  it("skips entries GradleVersion cannot parse instead of failing", () => {
    expect(reduceVersions([entry("not-a-version"), entry("9.6.1")]).versions).toEqual(["9.6.1"]);
  });

  it("throws on unusable payloads instead of producing an empty index", () => {
    expect(() => reduceVersions({ error: "nope" })).toThrow();
    expect(() => reduceVersions([entry("9.7.0-rc-1", { final: false })])).toThrow();
  });
});

describe("versionDocsUrl", () => {
  it("targets the user-manual index, which exists in every published version", () => {
    expect(versionDocsUrl("9.5.1")).toBe("https://docs.gradle.org/9.5.1/userguide/userguide.html");
    expect(versionDocsUrl("nightly")).toBe(
      "https://docs.gradle.org/nightly/userguide/userguide.html",
    );
  });
});

describe("renderVersionMenu", () => {
  const index: VersionsIndex = {
    current: "9.6.1",
    versions: ["9.6.1", "8.14.5"],
    activeRc: "9.7.0-rc-1",
    nightly: "9.7.0-20260706133305+0000",
  };

  it("renders major headers, badges, and links", () => {
    const html = renderVersionMenu(index, "9.6.1");
    expect(html).toContain(">Gradle 9</li>");
    expect(html).toContain(">Gradle 8</li>");
    expect(html).toContain(
      'href="https://docs.gradle.org/9.6.1/userguide/userguide.html" aria-current="true"',
    );
    expect(html).toContain('<span class="badge">latest</span>');
    expect(html).toContain('<span class="badge">RC</span>');
    expect(html).toContain('href="https://docs.gradle.org/nightly/userguide/userguide.html"');
    expect(html).toContain(">Nightly</a>");
    expect(html).toContain('href="https://gradle.org/releases/"');
  });

  it("sorts pre-releases to the bottom of their major group", () => {
    const html = renderVersionMenu(index, undefined);
    const order = [
      ">Gradle 9</li>",
      ">9.6.1<",
      ">Nightly<",
      ">9.7.0-rc-1<",
      ">Gradle 8</li>",
      ">8.14.5<",
    ];
    const positions = order.map((needle) => html.indexOf(needle));
    expect(positions.every((p) => p >= 0)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
  });

  it("gives a nightly for an unreleased major its own group", () => {
    const html = renderVersionMenu({ ...index, nightly: "10.0.0-20260706133305+0000" }, undefined);
    expect(html.indexOf(">Gradle 10</li>")).toBeGreaterThanOrEqual(0);
    expect(html.indexOf(">Gradle 10</li>")).toBeLessThan(html.indexOf(">Gradle 9</li>"));
  });

  it("escapes endpoint-supplied strings", () => {
    const evil = { ...index, versions: ['9.6.1"><img src=x>'], activeRc: null, nightly: null };
    const html = renderVersionMenu(evil, undefined);
    expect(html).not.toContain("<img");
  });
});
