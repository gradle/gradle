// Build-time values substituted into doc content by the
// remark-substitute-variables plugin (see astro.config.ts). The converter emits
// `%%name%%` tokens; this map resolves them at `astro build` time, so locked
// stage_2 pages pick up a version bump on rebuild instead of baking a stale one.
//
// For this experiment the values are stubbed here. The real wiring — the outer
// Gradle build supplying these via PUBLIC_GRADLE_* env vars — lives in
// gradle/gradle; `process.env` is the forward-compatible hook, with the stub as
// the local-dev fallback.
const fallbacks = {
  // REMOVEME: after migration, this should be removed; Gradle will always supply the value
  gradleVersion: "9.7.0",
} as const;

export type VariableName = keyof typeof fallbacks;

export const variables: Record<VariableName, string> = {
  gradleVersion: process.env.PUBLIC_GRADLE_VERSION ?? fallbacks.gradleVersion,
};
