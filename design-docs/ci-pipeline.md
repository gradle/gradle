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


# Add initial linux jdk9 coverage to Gradle CI pipeline

To verify Gradle working with jdk9 we need basic jdk9 coverage in our CI pipeline

## implementation

- add jdk9 installation the linux vm boxes~~
    - ~~update salt-master setup to download jdk9 from java.net~~
    - ~~update linux build vms to setup jdk9~~
        
- ~~provide howto about updating jdk9 EA installations in our dev-infrastructure~~
        
- ~~add  `Linux - Java 1.9` build configuration on teamcity~~    
    - ~~running `clean platformTest`~~
    - ~~for `master` pipeline~~
    - for `release` pipeline

## open issues
- we don't currently update the dev2 box as it is unmanaged. plan is to get rid of dev2 in the long run
- no 64bit windows jdk9 available yet (Build b60)
- convenient update of jdk9 early access releases


# Add windows jdk9 coverage to Gradle CI pipeline

At the moment there is no 64bit windows jdk9 available yet (Build b60)

## implementation

- add jdk9 installation to the windows vm boxes
    - update salt-master win-repo setup to download jdk9 from java.net
    - update windows build vms to install jdk9
- setup  `Windows - Java 1.9 - Quick test` build configuration on teamcity
    - running `clean quickTest`
    - for `master` pipeline
    - for `release` pipeline
    
## open issues
- convenient update of jdk9 early access releases

# Trigger the coverage builds less frequently

Currently, coverage builds are triggered for each commit. We might change this so that multiple queued up commits are merged into a single build.

- Do not trigger full pipeline for each commit
- Do not run multiple instances of Stage 4 (Full Coverage) in parallel
- Adjust Commit configurations such that concurrent commits by different developers lead to multiple builds with the commits grouped by developer
- Configure TC to automatically restart a coverage build if it has failed

# Checkout source on agents


# Reduce memory consumption of daemon processes started by test suite

- Verify that daemon processes are started with relatively small heap and permgen limits, rather than the defaults for the daemon, and fix if not.
- Kill daemons at the end of the build.

# Compile source against baseline Java version early in the pipeline

To fail early when later Java APIs are used.

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
- `BasicGroovyCompilerIntegrationSpec` and `BasicScalaCompilerIntegrationTest` that run tests against all combinations of (fork, compiler-back-end)

For example, we might

- change each of these to cover only a single version for dev and commit build (some of the above already do this, some don't)
- change each of these to cover only a single version for the environment coverage builds
- move coverage of the full set of versions to a nightly build

## Implementation

- Replace `ScalaCoverage` with a more general strategy
- Add some test infrastructure to deal with this in a consistent way

# Run some coverage builds less frequently

Possibly run these as nightly builds:

- Unknown OS coverage
- IBM JVM coverage

# Profile the commit and coverage builds

Investigate where the time is going and address this.

# Prioritise fast feedback over slow feedback

Tweak the teamcity settings so that:

- Release branch commit builds win over all master branch builds
- Commit builds for any branch win over all coverage builds for any branch
- Coverage builds for any branch win over nightly builds for any branch

Alternatively, move some agents to the fast feedback pool, to reserve bandwidth for the commit builds (revisit pools in general)

# Leverage incremental build

# Leverage parallel execution

# Make pipeline more likely to pass

Stabilise the flakey tests.

# Reduce size of artifacts

Reduce the size of the log output:

- size of artifacts for a single Windows Java 1.7 Cross-Version test is 1.75 GB
- can we bump up the log level threshold to reduce the amount of logs?

# Build against Early Access builds of the JDK

This would include new releases of 6, 7, 8 and 9.

# Prevent accumulation of junk in /tmp

Executing Gradle (to build and test Gradle) accumulates junk in the OS tmp dir.
A `tmpreaper` job should be executed regularly to keep this clean.

This would be obsolete if we completely rebuilt agent machines periodically. 

# Prevent accumulation of old wrappers

We are accumulating the wrappers for the versions of Gradle that we use to build Gradle itself.

This would be obsolete if we completely rebuilt agent machines periodically. 

# Prevent accumulation of junk in ~/.m2/repository 

Certain integration tests write to `~/.m2/repository which collects junk.

This would be obsolete if we completely rebuilt agent machines periodically. 
