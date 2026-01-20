# ADR-0009 - Use American English

## Date

2025-11-24

## Context

Gradle is an international open-source project with contributors from around the world. English is the primary language for code, comments, documentation, and communication, but there are variations in English spelling and terminology, specifically American English vs. British English.

Inconsistency in spelling conventions can lead to several issues:

* **Confusion for users and contributors**: Mixed spellings (e.g., "color" and "colour", "initialize" and "initialise") in the codebase create confusion about which variant to use
* **Harder code search and refactoring**: Searching for identifiers, method names, or documentation becomes more difficult when multiple spellings exist for the same concept
* **Inconsistent API surface**: Public APIs with mixed spelling conventions appear less professional and polished
* **Harder for non-native speakers**: Contributors and users for whom English is a second language benefit from a single, consistent convention

While both American and British English are valid, establishing a single standard improves consistency, readability, and maintainability of the codebase.

## Decision

The Gradle project adopts **American English** as the standard language variant for all code, comments, documentation, user-facing messages, and written communication.

### Examples

Use American English spelling, for example:

* `color` (not `colour`)
* `initialize` (not `initialise`)
* `behavior` (not `behaviour`)
* `analyze` (not `analyse`)
* `organization` (not `organisation`)
* `center` (not `centre`)
* `canceled` (not `cancelled`)
* `labeled` (not `labelled`)

### Rationale for American English

American English was chosen for the following reasons:

* **Current predominance**: The majority of existing Gradle code and documentation already uses American English
* **Industry standard**: Most widely used programming languages, frameworks, and tools (Java, Kotlin, JavaScript, etc.) use American English in their APIs and documentation
* **Consistency with dependencies**: Gradle's ecosystem and dependencies predominantly use American English

### Exceptions

Some legitimate exceptions exist where British English or other variants must be preserved:

* **Third-party APIs and libraries**: When integrating with external APIs that use different spelling conventions
* **Historical compatibility**: Existing public API methods that are part of Gradle's stable API cannot be renamed due to backward compatibility requirements (though new APIs should use American English)
* **Proper nouns and quotes**: Names of organizations, products, or quoted text that use different conventions

## Status

ACCEPTED

## Consequences

### Positive Consequences

* **Improved consistency**: The codebase has a uniform language standard
* **Easier onboarding**: New contributors have clear guidance on which spelling to use
* **Better searchability**: Finding code and documentation becomes more predictable
* **Reduced review friction**: Less time spent on spelling corrections during PR reviews
* **Professional appearance**: Consistent APIs and documentation appear more polished
* **Clearer contribution guidelines**: [CONTRIBUTING.md](../../CONTRIBUTING.md) now includes this requirement

### Negative Consequences

* **Historical inconsistencies**: Existing code may contain British English spellings that cannot be changed due to backward compatibility

### Migration and Enforcement

* **New code**: All new contributions must use American English
* **Existing code**: British English spellings in existing code should be updated opportunistically when those areas are modified, unless constrained by backward compatibility
* **Documentation**: Documentation should be updated to use American English, prioritizing user-facing documentation
* **Code review**: Reviewers should gently remind contributors about the American English standard when necessary

