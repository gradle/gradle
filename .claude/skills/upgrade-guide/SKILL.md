---
name: upgrade-guide
description: Gradle upgrade guide covering versions 8.0 through 9.5.0. Use when helping users upgrade Gradle, diagnose version-related build failures, or understand breaking changes between versions.
argument-hint: [from-version] [to-version]
---

# Gradle Upgrade Guide

Help the user upgrade their Gradle build between versions, or answer questions about breaking changes, deprecations, and migration strategies.

## How to use arguments

- If both `$0` (from-version) and `$1` (to-version) are provided, focus the response on changes between those two versions only.
- If only `$0` is provided, treat it as the target version and cover changes leading to it.
- If no arguments are provided, ask the user which versions they're upgrading between.

## Instructions

1. Read the full upgrade reference at [reference.md](reference.md) (co-located with this skill)
2. Extract only the sections relevant to the user's version range
3. Prioritize in this order:
   - Breaking changes that will cause build failures
   - Removed APIs (especially for major version jumps like 8.x to 9.x)
   - Deprecations that should be addressed
   - Community-reported issues and workarounds
   - Notable new features they can take advantage of
4. For each breaking change, include the code migration example if one exists in the reference
5. If upgrading across the 8.x to 9.0 boundary, emphasize:
   - JVM 17+ requirement
   - Minimum plugin versions (KGP 2.0.0, AGP 8.4.0, Develocity 3.13.1)
   - Convention API removal
   - Kotlin 2.2 / Groovy 4.0 / JSpecify nullability changes
6. Always recommend running `gradle help --warning-mode=all` on the current version before upgrading
