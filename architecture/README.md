# Gradle architecture documentation

This directory contains documentation that describes Gradle's architecture and how the various pieces fit together and work.

## Architecture decision records (ADRs)

The Gradle team uses ADRs to record architectural decisions that the team has made.

See [Architecture decisions records](standards) for the list of ADRs.
Be aware these are very technical descriptions of the decisions, and you might find the documentation below more useful as an introduction to the internals of Gradle.

## Platform architecture

Gradle is arranged into several coarse-grained components called "platforms".
Each platform provides support for some kind of automation, such as building JVM software or building Gradle plugins.
Most platforms typically build on the features of other platforms.

By understanding the Gradle platforms and their relationships, you can get a feel for where in the Gradle source a particular feature might be implemented.

See [Gradle platform architecture](platforms.md) for a list of the platforms and more details.

## Gradle runtimes

Gradle is also made up of several different processes that work together to "run the build", such as the Gradle daemon and the `gradlew` command.

Each process, or "runtime", applies different constraints to the code that runs in that process.
For example, each process has different supported JVMs and a different set of services available for dependency injection.
While a lot of Gradle source code runs only in the Gradle daemon, not all of it does and so, when working on some source code it is important to be aware of the runtimes in which it will run.

See [Gradle runtimes](runtimes.md) for a list of these runtimes and more details.

## Build state model

As Gradle executes, it acts on various pieces of the build definition, such as each project in the build.
Gradle tracks the state of each piece and transitions each piece through its lifecycle as the build runs.

A central part of the Gradle architecture is the "build state model", which holds the state for each piece and coordinates state transitions and other mutations. 
Most source code in Gradle is arranged by which part(s) of the build state model it acts on.
This affects the lifecycle of the code and the set of services available for dependency injection.
When working on some source code it is important to be aware of the model it acts on.  

See [build state model](build-state-model.md) for more details.
