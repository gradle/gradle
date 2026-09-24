/**
 * Migrated from Gradle's own GradleVersionTest
 * (platforms/core-runtime/logging/src/test/groovy/org/gradle/util/GradleVersionTest.groovy),
 * minus the specs about `current()` and build-receipt metadata, which the port
 * deliberately omits. The `where:` tables are kept verbatim.
 */
import { describe, expect, it } from "vitest";
import { GradleVersion } from "./gradle-version";

describe("GradleVersion", () => {
  it.each(["", "something", "1", "1-beta", "1.0-\n"])(
    "parsing fails for unrecognized version string %j",
    (versionString) => {
      expect(() => GradleVersion.version(versionString)).toThrow(
        `'${versionString}' is not a valid Gradle version string (examples: '9.0.0', '9.1.0-rc-1')`,
      );
    },
  );

  it("can parse commitId from commit version", () => {
    expect(GradleVersion.version("5.1-commit-123abc").commitId).toBe("123abc");
  });

  it("equals and hash code", () => {
    expect(GradleVersion.version("0.9").equals(GradleVersion.version("0.9"))).toBe(true);
    expect(GradleVersion.version("0.9").equals(GradleVersion.version("1.0"))).toBe(false);
  });

  it.each([
    "1.0",
    "12.4.5.67",
    "1.0-milestone-5",
    "1.0-milestone-5a",
    "3.2-rc-2",
    "3.0-snapshot-1",
    "5.1-commit-2149a1d",
  ])("can construct version from string %j", (version) => {
    const gradleVersion = GradleVersion.version(version);
    expect(gradleVersion.version).toBe(version);
    expect(gradleVersion.toString()).toBe(`Gradle ${version}`);
  });

  it.each([
    "0.9-20101220110000+1100",
    "0.9-20101220110000-0800",
    "1.2-20120501110000",
    "1.2-SNAPSHOT",
    "3.0-snapshot-1",
    "9.0.0-snapshot-1",
  ])("versions with timestamp are considered snapshots: %j", (version) => {
    const gradleVersion = GradleVersion.version(version);
    expect(gradleVersion.version).toBe(version);
    expect(gradleVersion.isSnapshot()).toBe(true);
  });

  it.each(["0.9-milestone-5", "2.1-rc-1", "1.2", "1.2.1"])(
    "versions without timestamp are not considered snapshots: %j",
    (version) => {
      expect(GradleVersion.version(version).isSnapshot()).toBe(false);
    },
  );

  function canCompareTwoVersions(a: string, b: string) {
    expect(GradleVersion.version(a).compareTo(GradleVersion.version(b))).toBeGreaterThan(0);
    expect(GradleVersion.version(b).compareTo(GradleVersion.version(a))).toBeLessThan(0);
    expect(GradleVersion.version(a).compareTo(GradleVersion.version(a))).toBe(0);
    expect(GradleVersion.version(b).compareTo(GradleVersion.version(b))).toBe(0);
  }

  it.each([
    ["0.9", "0.8"],
    ["1.0", "0.10"],
    ["10.0", "2.1"],
    ["2.5", "2.4"],
    ["9.0.0", "8.0"],
  ])("can compare major versions: %j > %j", canCompareTwoVersions);

  it.each([
    ["0.9.2", "0.9.1"],
    ["0.10.1", "0.9.2"],
    ["1.2.3.40", "1.2.3.8"],
    ["1.2.3.1", "1.2.3"],
    ["1.2.3.1.4.12.9023", "1.2.3"],
  ])("can compare point versions: %j > %j", canCompareTwoVersions);

  it.each([
    ["0.9.1", "0.9"],
    ["0.10", "0.9.1"],
  ])("can compare point version and major versions: %j > %j", canCompareTwoVersions);

  it.each([
    ["1.0-milestone-2", "1.0-milestone-1"],
    ["1.0-preview-2", "1.0-preview-1"],
    ["1.0-preview-1", "1.0-milestone-7"],
    ["1.0-rc-1", "1.0-milestone-7"],
    ["1.0-rc-2", "1.0-rc-1"],
    ["1.0-rc-7", "1.0-rc-1"],
    ["1.0", "1.0-rc-7"],
  ])("can compare previews, milestones and RC versions: %j > %j", canCompareTwoVersions);

  it.each([
    ["1.0-milestone-2a", "1.0-milestone-2"],
    ["1.0-milestone-2b", "1.0-milestone-2a"],
    ["1.0-milestone-3", "1.0-milestone-2b"],
    ["1.0", "1.0-milestone-2b"],
  ])("can compare patch version: %j > %j", canCompareTwoVersions);

  it.each([
    ["0.9-20101220110000+1100", "0.9-20101220100000+1100"],
    ["0.9-20101220110000+1000", "0.9-20101220100000+1100"],
    ["0.9-20101220110000-0100", "0.9-20101220100000+0000"],
    ["0.9-20101220110000", "0.9-20101220100000"],
    ["0.9-20101220110000", "0.9-20101220110000+0100"],
    ["0.9-20101220110000-0100", "0.9-20101220110000"],
    ["0.9", "0.9-20101220100000+1000"],
    ["0.9", "0.9-20101220100000"],
    ["0.9", "0.9-SNAPSHOT"],
    ["0.9", "0.9-snapshot-1"],
  ])("can compare snapshot versions: %j > %j", canCompareTwoVersions);

  it.each([
    ["5.1", "5.1-commit-123456789"],
    ["5.1", "5.1-commit-bcda90482104"],
    ["5.1-commit-1234", "5.0"],
    ["5.1-commit-1234abcdef", "4.10.2"],
    ["5.1-commit-1234", "5.0-commit-1234"],
    ["5.0-commit-222", "5.0-commit-111"],
    ["5.0-commit-f1efb03", "5.0-commit-f1efb02"],
  ])("can compare commit versions: %j > %j", canCompareTwoVersions);

  it.each([
    ["1.0", "1.0"],
    ["1.0-rc-1", "1.0"],
    ["1.2.3.4", "1.2.3.4"],
    ["0.9", "0.9"],
    ["0.9.2", "0.9.2"],
    ["0.9-20101220100000+1000", "0.9"],
    ["0.9-20101220100000", "0.9"],
    ["20.17-20101220100000+1000", "20.17"],
    ["0.9-SNAPSHOT", "0.9"],
    ["3.0-snapshot-1", "3.0"],
    ["3.0-milestone-3", "3.0"],
    ["3.0-milestone-3-20121012100000+1000", "3.0"],
    ["9.0.0", "9.0.0"],
    ["9.0.0-rc-3", "9.0.0"],
    ["9.0.0-milestone-3", "9.0.0"],
    ["9.0.0-20251220100000+0400", "9.0.0"],
  ])("can get version base: %j -> %j", (v, base) => {
    expect(GradleVersion.version(v).baseVersion.equals(GradleVersion.version(base))).toBe(true);
  });

  it.each([
    ["1.0", "2.0"],
    ["1.0-rc-1", "2.0"],
    ["0.9-20101220100000+1000", "1.0"],
    ["0.9-20101220100000", "1.0"],
    ["20.17-20101220100000+1000", "21.0.0"],
    ["0.9-SNAPSHOT", "1.0"],
    ["3.0-snapshot-1", "4.0"],
    ["5.1-milestone-1", "6.0"],
    ["1.0-milestone-3", "2.0"],
    ["1.0-milestone-3-20121012100000+1000", "2.0"],
    ["2.0-milestone-3", "3.0"],
    ["8.1", "9.0.0"],
    ["9.1.1", "10.0.0"],
  ])("can get next major version: %j -> %j", (v, major) => {
    expect(GradleVersion.version(v).nextMajorVersion.equals(GradleVersion.version(major))).toBe(
      true,
    );
  });

  it.each([
    ["1.0", true],
    ["1.0-rc-1", false],
    ["1.0-milestone-1", false],
    ["1.0-milestone-1-20121012100000+1000", false],
    ["1.0-milestone-1-SNAPSHOT", false],
    ["1.0-SNAPSHOT", false],
    ["9.4.4-branch-XX-20121012100000+1000", false],
    ["8.14.1", true],
    ["9.0.0", true],
  ])("can check if is final: %j -> %j", (v, isFinal) => {
    expect(GradleVersion.version(v).isFinal()).toBe(isFinal);
  });

  describe("extensions (not in the upstream class)", () => {
    it("tryParse returns null instead of throwing", () => {
      expect(GradleVersion.tryParse("9.6.1")?.version).toBe("9.6.1");
      expect(GradleVersion.tryParse("not-a-version")).toBeNull();
    });

    it.each([
      ["9.6.1", "9.6"],
      ["9.6", "9.6"],
      ["1.2.3.4", "1.2"],
      ["9.7.0-rc-1", "9.7"],
      ["9.7.0-20260706133305+0000", "9.7"],
    ])("minor of %j is %j", (version, minor) => {
      expect(GradleVersion.version(version).minor).toBe(minor);
    });
  });
});
