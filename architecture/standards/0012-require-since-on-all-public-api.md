# ADR-0012 - Require @since on every public API element

## Status

- PROPOSED on 2026-07-16

## Context

Every element of Gradle's public API carries a `@since` tag that records the Gradle version in which it was introduced.

Historically, the requirement was enforced only at the *type* level, and a member was allowed to inherit its declaring type's `@since`.
The result is that a whole class could be introduced with a single class-level `@since`, and none of its methods would carry the tag — so none of them show a version in the DSL reference or in a quick documentation provided by IDEs.
This is cumbersome for Gradle Engineers, Plugin Authors, and Build Engineers.

## Decision

Each public API type and member (method, constructor, field, enum constant) MUST have an explicit `@since` annotation.
A type's `@since` is no longer implied onto its members: each member is checked on its own.

The following are exempt, because they have no source declaration of their own on which to place the tag (annotating them is either impossible or would document a supertype's or the compiler's element):

* Methods annotated `@Override` — the tag lives on the supertype declaration.
* Constructors annotated `@Inject` — not considered public API.
* Compiler-generated members with no source declaration:
  * Java: the implicit default constructor of a class that declares no constructor; the implicit methods of enums.
  * Kotlin: the implicit methods and fields of enums, `data class`; `object` and companion objects; synthetic members; members inherited from a supertype, including those supplied by delegation.

The requirement is enforced by the binary compatibility check, which compares the current API against the previous release and so applies to every newly added or changed public API element.

## Consequences

Adding a type or a member to an existing type now always requires a `@since` entry for that element.

The requirement binds new and changed API.
The pre-existing public API — including `protected` members, which are a (hidden) part of the public API — was retro-fitted with `@since` derived from the released distributions.

