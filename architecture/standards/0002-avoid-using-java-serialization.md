# ADR-0002 - Avoid using Java serialization

## Date

2012-12-01

## Context

In Gradle we often need to serialize in-memory objects for caching, or to transmit them across process barriers, etc.
Java serialization is one way to implement this, however, despite its simplicity of implementation, it has several drawbacks:

- **Performance:**
Java's built-in serialization mechanism is often slower compared to other serialization solutions.
This is due to Java's use of reflection and the need to maintain a lot of metadata.

- **Size of Serialized Data:**
Java serialization tends to produce larger serialized objects because it includes class metadata and other overhead.

- **Flexibility and Control:**
Java serialization offers limited control over the serialization process, such as excluding certain fields, customizing naming conventions, and handling complex data structures more gracefully.

- **Security:**
Java serialization poses security risks, especially related to deserialization vulnerabilities.

- **Version Compatibility:**
With Java serialization, even minor changes to a class (like adding a field) can break compatibility.

- **Cross-Language Compatibility:**
Java serialization is inherently Java-centric and does not support cross-language scenarios well.

- **Type Safety:**
Java serialization does not enforce type safety as strictly as some alternatives, potentially leading to runtime errors.

## Decision

We do not use Java serialization.
Instead, we use custom serialization where we explicitly describe how data objects should be serialized and deserialized.

For internal purposes, we use binary formats for their brevity.
We use the `Serializer` abstraction to separate the actual implementation of serialization from its uses.

When sharing data with external tools, we use JSON.

## Status

ACCEPTED

## Consequences

* The configuration cache serialization infrastructure should be used for all serialization.
* Existing usages of Serializer outside of this infrastructure should be migrated to use it.
* Existing usages of Java serialization should be migrated to use it.
* It is ok for Serializer to be used as a replacement for Java serialization as a migration step.
* The configuration cache uses the `Serializer`s based on the [Kryo framework](https://github.com/EsotericSoftware/kryo) for most serialization needs.
