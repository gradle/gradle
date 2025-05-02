# ADR-0007 - Support running Gradle on multiple Java versions and do not embed a Java runtime in the Gradle distribution 

## Date

2024-12-20

## Context

### Embedding a Java runtime in the Gradle distribution

There have been discussions on embedding a Java runtime in the Gradle distribution.
This would allow users to run Gradle without having to install a Java runtime beforehand.
This capability could help increase Gradle adoption outside of the JVM ecosystem.

Gradle can be invoked in different ways:
* Through the [Gradle Wrapper](https://docs.gradle.org/8.8/userguide/gradle_wrapper.html)
  * It currently requires a JVM and will download the Gradle distribution if it is not already present.
* A Gradle version installed on the system
  * This starts the [Gradle Launcher](https://blog.gradle.org/how-gradle-works-1#local-gradle-distribution-in-cli), which itself requires a JVM
* The [Gradle Tooling API client](https://docs.gradle.org/8.8/userguide/third_party_integration.html#embedding)
  * This requires a Java application, and thus runtime, to run the tooling API client

As indicated, each of those components require a Java runtime to run.

The recommended way of invoking Gradle is to use the Gradle Wrapper.
The Wrapper then downloads the Gradle distribution (if necessary), starts the Launcher and runs the build, spawning and connecting to other processes such as the Daemon as required.

Embedding a Java runtime in the distribution would provide some benefits, such as allowing the Launcher, Daemon and Workers to run on it, removing the prerequisite of an installed Java runtime.
However, this does not fully remove the prerequisite, as the Wrapper itself would still need an installed Java runtime to execute.

Gradle needs a solution that covers the use-case of starting the Wrapper itself as well given its importance.

### Running Gradle on multiple Java versions

In addition, some of these discussions included proposals for having a single Java version supported by the Gradle Launcher and Daemon.
However, this would limit the ability of the Gradle ecosystem plugin authors to take advantage of new Java features and improvements.

One example is when one of the most popular plugins started requiring Java 17 because the underlying framework behind the plugin began requiring 17, and code from the framework was used in the plugin itself.
What would have happened if Gradle was limited to _only Java 11_ at the time?

## Decision

1. The Gradle distribution will never include a Java runtime.
   Instead, Gradle will leverage Java toolchains for the Daemon and worker processes.
2. The Gradle Daemon, Worker processes, and Tooling API client will continue to support running on different Java versions during a single Gradle invocation.
   The exact versions supported will be determined by the Gradle version.
3. Regarding the Tooling API client, it is the responsibility of the application embedding it to provide the Java runtime.

## Status

PROPOSED

## Consequences

- Finalize Daemon JVM toolchain support, including auto-provisioning.
- To stop requiring a pre-installed Java runtime for the Gradle Wrapper and Launcher, Gradle will need to find an alternative.
A native client could be such a solution, using native image capabilities, another language, or another future technology.
