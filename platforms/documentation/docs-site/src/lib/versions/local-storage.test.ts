import { afterEach, describe, expect, it, vi } from "vitest";
import {
  isFresh,
  isVersionsIndex,
  loadStoredIndex,
  storeIndex,
  type VersionsIndex,
} from "./local-storage";

const index: VersionsIndex = {
  schema: 1,
  fetchedAt: 1000,
  current: "9.6.1",
  versions: ["9.6.1", "8.14.5"],
  activeRc: null,
  nightly: "9.7.0-20260706133305+0000",
};

describe("isVersionsIndex", () => {
  it("accepts a well-formed index and rejects junk", () => {
    expect(isVersionsIndex(index)).toBe(true);
    expect(isVersionsIndex(null)).toBe(false);
    expect(isVersionsIndex({ ...index, schema: 2 })).toBe(false);
    expect(isVersionsIndex({ ...index, versions: [42] })).toBe(false);
  });

  it("rejects cached indexes from before the RC/nightly fields existed", () => {
    const { activeRc: _rc, nightly: _n, ...old } = index;
    expect(isVersionsIndex(old)).toBe(false);
  });
});

describe("load/store", () => {
  const backing = new Map<string, string>();
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => backing.get(key) ?? null,
    setItem: (key: string, value: string) => backing.set(key, value),
  });
  afterEach(() => backing.clear());

  it("round-trips a stored index", () => {
    storeIndex(index);
    expect(loadStoredIndex()).toEqual(index);
  });

  it("returns undefined for missing, corrupt, or invalid data", () => {
    expect(loadStoredIndex()).toBeUndefined();
    backing.set("gradle-docs.versions.v1", "{not json");
    expect(loadStoredIndex()).toBeUndefined();
    backing.set("gradle-docs.versions.v1", JSON.stringify({ schema: 99 }));
    expect(loadStoredIndex()).toBeUndefined();
  });

  it("never throws when storage is unavailable", () => {
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new Error("blocked");
      },
      setItem: () => {
        throw new Error("quota");
      },
    });
    expect(loadStoredIndex()).toBeUndefined();
    expect(() => storeIndex(index)).not.toThrow();
  });
});

describe("isFresh", () => {
  it("compares against the TTL", () => {
    expect(isFresh(index, 1000)).toBe(true);
    expect(isFresh(index, 1000 + 24 * 60 * 60 * 1000)).toBe(false);
  });
});
