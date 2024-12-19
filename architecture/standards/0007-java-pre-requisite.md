# ADR-0007 - Gradle and Java as a pre-requisite

## Date

2024-06-26

## Context

There have been discussions on embedding a Java runtime in the Gradle distribution.
This would allow users to run Gradle without having to install a Java runtime beforehand.
Which is something that can ease Gradle adoption outside of the JVM ecosystem.

Gradle has different runtime components:
* The [Gradle Wrapper](https://docs.gradle.org/8.8/userguide/gradle_wrapper.html)
* The [Gradle Distribution](https://gradle.org/releases/) which contains
  * the [Gradle Launcher](https://blog.gradle.org/how-gradle-works-1#local-gradle-distribution-in-cli)
  * the [Gradle Daemon](https://docs.gradle.org/8.8/userguide/gradle_daemon.html)
  * the [Gradle Worker processes](https://docs.gradle.org/8.8/userguide/worker_api.html)
* The [Gradle Tooling API client](https://docs.gradle.org/8.8/userguide/third_party_integration.html#embedding)

At the moment, each of those components require a Java runtime to run.

The recommended way of integrating a project with Gradle is to use the Gradle Wrapper.
The Wrapper then downloads the Gradle distribution, starts the Launcher and runs the build inside the Daemon.

Embedding a Java runtime in the distribution would provide benefits.
The Launcher, Daemon and Workers could run on it, removing the pre-requisite of an installed Java runtime.
However, this does not fully remove the pre-requisite, as the Wrapper would still need a Java runtime.

Gradle needs a solution that covers the Wrapper use-case as well given its importance.

In addition, some of these discussions included conversation about having a single Java version supported by the Gradle Launcher and Daemon.
However, this would limit the ability of the Gradle ecosystem of plugin authors to take advantage of new Java features and improvements.
One example is when a popular plugin started requiring Java 17 because the underlying framework behind the plugin did and it is used in the plugin itself.
What would have happened if Gradle was limited to Java 11 at the time?

## Decision

1. The Gradle distribution will never include a Java runtime.
   Instead, Gradle will leverage Java toolchains for the Daemon and worker processes.
2. To stop requiring a pre-installed Java runtime for the Gradle Wrapper and Launcher, Gradle will, in the future, develop a native version of those.
3. The Gradle Daemon, Worker processes, and Tooling API client will support running on more than one Java version.
   The exact versions supported will be determined by the Gradle version.
4. Regarding the Tooling API client, it is the responsibility of the application embedding it to provide the Java runtime.

## Status

PROPOSED

## Consequences

- Finalize Daemon JVM toolchain support, including auto-provisioning.
- Continue investigation into native options for the Gradle Wrapper and Launcher.
