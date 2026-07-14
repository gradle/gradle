# API Changes

## Java and nullability

- Always use JSpecify annotations like `@Nullable` and `@NullMarked`
- Always mark new packages as `@NullMarked`
- Never use nullability annotations from other modules like `javax.annotation`

## What is Gradle Public API?

The Gradle Public API scope is defined in:

- `build-logic-commons/basics/src/main/kotlin/gradlebuild/basics/PublicApi.kt`
- `build-logic-commons/basics/src/main/kotlin/gradlebuild/basics/PublicKotlinDslApi.kt`

A type is part of the Gradle public API if and only if its FQCN

- matches `PublicApi.includes` or `PublicKotlinDslApi.includes`
- and does not match `PublicApi.excludes` nor `PublicKotlinDslApi.excludes`

When a type is not part of the Gradle Public API, it is considered "internal".

## Gradle Internal API Changes

When making changes to internal code, don't consider backwards compatibility.

## Gradle Public API Changes

Try to minimize the changes to the Gradle Public API.

### Adding new public API elements

New public API types or members must be marked with BOTH:

- `@Incubating`
- `@since <full-version>` using the three-component form, e.g. `@since 9.6.0`,
  taken from `version.txt`. A two-component form like `@since 9.6` is NOT
  accepted by the binary-compatibility check.

When both annotations are applied correctly, the new element is exempt from `accepted-public-api-changes.json`.
Do NOT add an entry for newly incubating API.

If `./gradlew sanityCheck` flags a new `@Incubating` member as binary-incompatible, the fix is to correct the annotation (missing `@Incubating`,
wrong `@since` format, or annotation on a non-public visibility) — NOT to silence the check via `accepted-public-api-changes.json`.

### Modifying or removing existing public API

Changes that are not new additions (signature changes, removals, visibility reductions, promotion out of `@Incubating`) must be validated:

1. Run `./gradlew sanityCheck` to see binary compatibility errors.
2. If the change is intentional and approved, add an entry to `accepted-public-api-changes.json`.
3. Sort the file: `./gradlew :architecture-test:sortAcceptedApiChanges`.
4. Re-run `./gradlew sanityCheck` to verify.

### Heuristic

- Adding? `@Incubating` + `@since 9.x.y`. No JSON entry.
- Changing / removing? JSON entry.
- `sanityCheck` failing on something you just added? Annotation is wrong.
