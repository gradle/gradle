/**
 * TypeScript port of Gradle's own version model,
 * `org.gradle.util.internal.DefaultGradleVersion` (Apache License 2.0), so the
 * version selector parses, orders, and classifies versions exactly like Gradle
 * itself: milestone < preview < rc < final, stage patch letters (`milestone-2a`),
 * snapshot timestamps with zone offsets, and `commit`/branch versions.
 *
 * Omitted from the original: `current()` and the build-receipt metadata
 * (build timestamp, commit ids of the running distribution), which are JVM
 * runtime concerns.
 */

const VERSION_PATTERN = /^((\d+)(\.\d+)+)(-([a-zA-Z]+)-(\w+))?(-(SNAPSHOT|\d{14}([-+]\d{4})?))?$/;

const STAGE_MILESTONE = 0;
const STAGE_UNKNOWN = 1;
const STAGE_PREVIEW = 2;
const STAGE_RC = 3;

class Stage {
  private constructor(
    readonly stage: number,
    readonly number: number,
    readonly patchNo: string,
  ) {}

  static from(stage: number, stageString: string): Stage | null {
    const match = /^(\d+)([a-z])?$/.exec(stageString);
    if (!match) return null;
    // "_" sorts below "a"–"z", so an unpatched stage precedes its patches.
    return new Stage(stage, parseInt(match[1], 10), match[2] ?? "_");
  }

  compareTo(other: Stage): number {
    if (this.stage !== other.stage) return this.stage > other.stage ? 1 : -1;
    if (this.number !== other.number) return this.number > other.number ? 1 : -1;
    if (this.patchNo !== other.patchNo) return this.patchNo > other.patchNo ? 1 : -1;
    return 0;
  }
}

export class GradleVersion {
  readonly version: string;
  readonly majorVersion: number;
  readonly commitId: string | null;
  private readonly versionPart: string;
  private readonly stage: Stage | null;
  /** Epoch millis of the snapshot timestamp; 0 for undated snapshots; null for releases. */
  private readonly snapshot: number | null;

  /**
   * Parses the given string into a GradleVersion.
   *
   * @throws Error on an unrecognized version string.
   */
  static version(version: string): GradleVersion {
    return new GradleVersion(version);
  }

  private constructor(version: string) {
    this.version = version;
    const match = VERSION_PATTERN.exec(version);
    if (!match) {
      throw new Error(
        `'${version}' is not a valid Gradle version string (examples: '9.0.0', '9.1.0-rc-1')`,
      );
    }

    this.versionPart = match[1];
    this.majorVersion = parseInt(match[2], 10);

    const isCommitVersion = match[5] === "commit";
    this.commitId = isCommitVersion ? match[6] : null;
    this.stage = this.parseStage(match, isCommitVersion);
    this.snapshot = this.parseSnapshot(match, isCommitVersion);
  }

  private parseStage(match: RegExpExecArray, isCommitVersion: boolean): Stage | null {
    if (match[4] === undefined || isCommitVersion) return null;
    switch (match[5]) {
      case "milestone":
        return Stage.from(STAGE_MILESTONE, match[6]);
      case "preview":
        return Stage.from(STAGE_PREVIEW, match[6]);
      case "rc":
        return Stage.from(STAGE_RC, match[6]);
      default:
        return Stage.from(STAGE_UNKNOWN, match[6]);
    }
  }

  private parseSnapshot(match: RegExpExecArray, isCommitVersion: boolean): number | null {
    if (match[5] === "snapshot" || isCommitVersion) return 0;
    if (match[8] === undefined) return null;
    if (match[8] === "SNAPSHOT") return 0;

    // yyyyMMddHHmmss, with an optional [-+]HHmm zone offset (UTC when absent).
    const t = match[8];
    const utc = Date.UTC(
      parseInt(t.slice(0, 4), 10),
      parseInt(t.slice(4, 6), 10) - 1,
      parseInt(t.slice(6, 8), 10),
      parseInt(t.slice(8, 10), 10),
      parseInt(t.slice(10, 12), 10),
      parseInt(t.slice(12, 14), 10),
    );
    const zone = match[9];
    if (zone === undefined) return utc;
    const offsetMinutes = parseInt(zone.slice(1, 3), 10) * 60 + parseInt(zone.slice(3, 5), 10);
    const offsetMillis = (zone[0] === "-" ? -1 : 1) * offsetMinutes * 60 * 1000;
    return utc - offsetMillis;
  }

  toString(): string {
    return `Gradle ${this.version}`;
  }

  isSnapshot(): boolean {
    return this.snapshot !== null;
  }

  get baseVersion(): GradleVersion {
    if (this.isFinal()) return this;
    return GradleVersion.version(this.versionPart);
  }

  isFinal(): boolean {
    return this.stage === null && this.snapshot === null;
  }

  get nextMajorVersion(): GradleVersion {
    if (this.majorVersion >= 8) {
      return GradleVersion.version(`${this.majorVersion + 1}.0.0`);
    }
    return GradleVersion.version(`${this.majorVersion + 1}.0`);
  }

  compareTo(other: GradleVersion): number {
    const parts = this.versionPart.split(".");
    const otherParts = other.versionPart.split(".");

    for (let i = 0; i < parts.length && i < otherParts.length; i++) {
      const part = parseInt(parts[i], 10);
      const otherPart = parseInt(otherParts[i], 10);
      if (part > otherPart) return 1;
      if (otherPart > part) return -1;
    }
    if (parts.length > otherParts.length) return 1;
    if (parts.length < otherParts.length) return -1;

    if (this.stage !== null && other.stage !== null) {
      const diff = this.stage.compareTo(other.stage);
      if (diff !== 0) return diff;
    }
    if (this.stage === null && other.stage !== null) return 1;
    if (this.stage !== null && other.stage === null) return -1;

    const thisSnapshot = this.snapshot ?? Number.MAX_VALUE;
    const otherSnapshot = other.snapshot ?? Number.MAX_VALUE;
    if (thisSnapshot === otherSnapshot) {
      return this.version < other.version ? -1 : this.version > other.version ? 1 : 0;
    }
    return thisSnapshot < otherSnapshot ? -1 : 1;
  }

  equals(other: GradleVersion): boolean {
    return this.version === other.version;
  }

  // -------------------------------------------------------------------------
  // Extensions beyond the upstream Java class, for this site's needs.
  // Everything above this line stays diffable against DefaultGradleVersion.
  // -------------------------------------------------------------------------

  /** Like {@link GradleVersion.version}, but returns null instead of throwing. */
  static tryParse(version: string): GradleVersion | null {
    try {
      return GradleVersion.version(version);
    } catch {
      return null;
    }
  }

  /** Minor-release key, e.g. "9.6" for 9.6.1: the first two numeric segments. */
  get minor(): string {
    return this.versionPart.split(".").slice(0, 2).join(".");
  }
}
