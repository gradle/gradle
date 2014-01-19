
Some ideas to improve feedback from the CI pipeline:

# Background

Here is a rough overview of the current structure of the CI pipeline

- Dev build: the build run by a developer prior to pushing changes
- Commit build: an initial verification stage early in the CI pipeline, triggered by each commit
- Coverage build: later stages of the CI pipeline. There are three kinds of coverage build:
    - Validate the entire Gradle product for some environment, such as Java 5 on Linux.
    - Validate the entire Gradle product in some execution mode, such as parallel or in the daemon.
    - Validate the entire Gradle product against the full range of supported versions of various integrations.
- Nightly build: later stages of the CI pipeline, triggered periodically, currently once per day.
    - Performance tests
    - Publish and smoke tests snapshots

This pipeline is replicated for the release and master branches.

# Reduce memory consumption of the full tooling API test suite

Currently, the full cross version integration test suite for the tooling API starts daemons for every Gradle version, and starts
multiple daemons for each version.

- Verify that many daemon processes are running while the test suite is executing.
- Verify that daemon processes are started with relatively small heap and permgen limits, rather than the defaults for the daemon, and fix if not.
- Change test execution for the tooling API test suite so that the tests for a single Gradle version (or small set of versions) are completed before starting
  tests on another Gradle version. One potential implementation is to introduce a test task per Gradle version.

# Split builds up so that each build covers a smaller slice of the source

Currently, most builds cover the entire Gradle code-base for a particular environment. We might split this up so that each build covers
only a slice through the Gradle code. This has some advantages:

- Different slices can be run in parallel by different agents
- When something fails due to some instability, less work needs to be performed when the pipeline is retriggered
- Provides somewhat finer-grained feedback

For example we might:

- Split core projects, such as core, coreImpl, launcher from the remaining projects.
- Trigger core projects only when the core source or build infrastructure changes.
- Trigger other projects when anything changes.
- Restructure the source layout to simplify this.
- Possibly for now keep the daemon and parallel tests as a single build, as this provides some useful stress. Replace this with real stress tests at some point.

# Run fewer multi-version tests in the earlier stages

There are several kinds of multi-version tests:

- `CrossVersionIntegrationSpec` subclasses that run against multiple older Gradle releases
- `MultiVersionIntegrationSpec` subclasses that run some integration against multiple versions of some external tool (eg multiple Groovy versions)
- `ToolingApiSpecification` subclasses that run against various combinations of current and older Gradle releases
- `AbstractInstalledToolChainIntegrationSpec` subclasses that run against the native toolchains that are available on the current machine

For example, we might

- change each of these to cover only a single version for dev and commit build (some of the above already do this, some don't)
- change each of these to cover only a single version for the environment coverage builds
- move coverage of the full set of versions to a nightly build

# Run some coverage builds less frequently

Possibly run these as nightly builds:

- Unknown OS coverage
- IBM JVM coverage

# Profile the commit and coverage builds

Investigate where the time is going and address this.

# Merge commits for coverage builds

Currently, coverage builds are triggered for each commit. We might change this so that multiple queued up commits are merged into a single build.

# Prioritise fast feedback over slow feedback

Tweak the teamcity settings so that:

- Release branch commit builds win over all master branch builds
- Commit builds for any branch win over all coverage builds for any branch
- Coverage builds for any branch win over nightly builds for any branch

Also remove the fast feedback agent pool

# Leverage incremental build

# Leverage parallel execution

# Run all Windows builds with virtual agents

At the moment running multiple Windows builds with virtual agents in parallel may cause memory issues. As a result the build fails. One of the observed error message
we see is the following:

    Error occurred during initialization of VM
    Could not reserve enough space for object heap

This error mainly occurs if one of the builds spawns new Gradle processses. To mitigate this situation the following builds are configured to only use the physical
Windows machine `winagent perf1`:

- Windows - Java 1.5 - Daemon integration tests
- Windows - Java 1.6 - Cross-version tests

All other builds are still using the virtual agents. After identifying and fixing the root cause for the error, we should change back the configuration.
