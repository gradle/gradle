## Goal

Make Gradle faster in many common cases.

Deliver the performance improvements that have already been implemented in the Gradle daemon
to more users, by enabling the Gradle daemon by default.

### Audience

The improvements are aimed at *developers* working in the Java and Android ecosystems and whose builds use the
current software model and who are using the Gradle daemon (possibly implicitly through the IDE).

The improvements will be general purpose and so will benefit all Gradle users, to greater and lesser degrees.

### Out of scope

Out-of-scope are the following improvements:

- Faster builds for other ecosystems: native, Play, Scala, Groovy, web.
- Faster builds for software model builds.
- Faster builds for build authoring and other use cases outside the dev loop.
- Faster builds for non-daemon usage.
- Making Java compilation faster, including improvements to incremental compile or adding compile avoidance.
- Improving parallel project execution
- Improving configure on demand

Here 'out-of-scope' means only that these use cases won't be specifically prioritized. If these use cases happen to improve due to other work, then that's a good thing.

## Features

- [ ] [More efficient performance metrics gathering](more-efficient-performance-metrics-gathering)
    - Infrastructure changes to improve efficiency when using or developing performance tests
- [ ] [Faster test execution startup](faster-test-execution-startup)
    - Maven vs Gradle benchmarks
    - Investigate hotspots in test execution startup
    - Improve progress logging to indicate the actual start of test execution
- [ ] [Faster build startup](faster-build-startup)
    - Reduce per build and per project fixed costs
    - Fix startup and configuration hotspots
    - Improve progress logging to give better insight to startup
- [ ] [Faster incremental builds](faster-incremental-builds)
    - Faster up-to-date checks
    - Faster dependency resolution
- [ ] [Daemon on by default for all users](daemon-on-by-default)
    - [Daemon uses fewer developer machine resources](daemon-on-by-default/daemon-uses-fewer-resources)
    - [Daemon is robust](daemon-on-by-default/daemon-is-robust)
    - [Support all use cases that are supported by non-daemon execution](daemon-on-by-default/daemon-use-case-parity)
    - [Fix Windows specific blockers](daemon-on-by-default/windows-blockers)
    - Enable by default
- [ ] [Tooling API performance improvements](tapi-performance-improvements)
    - Setup performance tests dedicated to the Tooling API
