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

  it("renders major headers, badges, and links from the newest build", () => {
    // Built from the 9.7.0 nightly, so every other entry is at or below it.
    const html = renderVersionMenu(index, "9.7.0-20260706133305+0000");
    expect(html).toContain(">Gradle 9</li>");
    expect(html).toContain(">Gradle 8</li>");
    expect(html).toContain('<span class="badge">latest</span>');
    expect(html).toContain('<span class="badge">RC</span>');
    expect(html).toContain('href="https://docs.gradle.org/nightly/userguide/userguide.html"');
    expect(html).toContain(">Nightly</a>");
    expect(html).toContain('href="https://gradle.org/releases/"');
  });

  it("marks the page's own version as current", () => {
    expect(renderVersionMenu(index, "9.6.1")).toContain(
      'href="https://docs.gradle.org/9.6.1/userguide/userguide.html" aria-current="true"',
    );
  });

  it("shortlists by the end-of-life policy instead of listing every release", () => {
    const wide: VersionsIndex = {
      current: "9.7.1",
      versions: [
        "9.7.1",
        "9.6.1",
        "9.5.1",
        "9.4.1",
        "9.3.0",
        "8.14.5",
        "8.13.0",
        "7.6.6",
        "7.5.1",
        "6.9.4",
        "5.6.4",
      ],
      activeRc: null,
      nightly: null,
    };
    const html = renderVersionMenu(wide, "9.7.1");
    // Three most recent minors of the current major, then the last minor of
    // each of the two preceding majors.
    for (const shown of ["9.7.1", "9.6.1", "9.5.1", "8.14.5", "7.6.6"]) {
      expect(html).toContain(`/${shown}/`);
    }
    // Everything else belongs behind "All releases".
    for (const hidden of ["9.4.1", "9.3.0", "8.13.0", "7.5.1", "6.9.4", "5.6.4"]) {
      expect(html).not.toContain(`/${hidden}/`);
    }
  });

  it("names the support status of the older majors", () => {
    const wide: VersionsIndex = {
      current: "9.7.1",
      versions: ["9.7.1", "9.6.1", "9.5.1", "8.14.5", "7.6.6"],
      activeRc: null,
      nightly: null,
    };
    const html = renderVersionMenu(wide, "9.7.1");
    expect(html).toContain('<span class="badge">latest</span>');
    expect(html).toContain('<span class="badge">maintenance</span>');
    expect(html).toContain('<span class="badge">EOL</span>');
  });

  it("keeps the build's own minor in the shortlist when a newer patch exists", () => {
    // The index holds 9.7.1 for the 9.7 line, but this is the 9.7.0 build.
    const wide: VersionsIndex = {
      current: "9.7.1",
      versions: ["9.7.1", "9.6.1", "9.5.1", "9.4.1", "8.14.5"],
      activeRc: null,
      nightly: null,
    };
    const html = renderVersionMenu(wide, "9.7.0");
    expect(html).toContain('/9.7.0/userguide/userguide.html" aria-current="true"');
    // 9.7.0 takes the 9.7 slot rather than letting an extra older minor in.
    expect(html).not.toContain("/9.4.1/");
  });

  it("lists only versions at or below the build, so an archived page never drifts", () => {
    const html = renderVersionMenu(index, "8.14.5");
    expect(html).toContain("8.14.5");
    // 9.6.1 shipped after 8.14.5, so an 8.14.5 page must not offer it.
    expect(html).not.toContain("9.6.1");
    // Nor the pre-releases of a later version.
    expect(html).not.toContain('<span class="badge">RC</span>');
    expect(html).not.toContain(">Nightly</a>");
  });

  it("lists the build's own version even when the index has only a newer patch", () => {
    // reduceVersions keeps 9.6.1 and drops 9.6.0, so a 9.6.0 build would
    // otherwise be missing from its own dropdown.
    const html = renderVersionMenu(index, "9.6.0");
    expect(html).toContain(
      'href="https://docs.gradle.org/9.6.0/userguide/userguide.html" aria-current="true"',
    );
    expect(html).not.toContain("9.6.1");
  });

  it("always offers Current, pointing at the same page under the alias", () => {
    const html = renderVersionMenu(index, "8.14.5", "/userguide/releases/installation/");
    expect(html).toContain(
      '<li class="current-version"><a href="https://docs.gradle.org/current/userguide/releases/installation/">Current</a></li>',
    );
  });

  it("labels Current without a version number, so it cannot go stale", () => {
    const html = renderVersionMenu(index, "8.14.5");
    expect(html).toContain(">Current</a>");
    expect(html).not.toMatch(/>Current \([0-9]/);
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
