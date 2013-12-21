# QA and Release Automation redesign

In the post 1.0 world, we are rethinking how we do QA and releases. This document outlines the design of the delivery pipeline and process.

There are no proposed changes to our current branching model. The way that we manage the `master` and `release` branches will stay the same.

## Goals

The following are the goals that must be met:

### Mandatory 

#### QA of a production like version of Gradle

Our integration testing should be based on testing something that resembles a production distribution. This means building the distribution in the master build environment (Java 1.7 on UNIX), and using these binaries to test compatibility with different platforms and modes.

#### CI server based releases

All builds/releases should be performed by the CI server. 

#### Ensure QA parity between the `master` and `release` branches 

Both branches should be tested in the same way, and have the same coverage.

#### Release candidates should be temporal

Currently, we list the RCs on the website as valid versions. We should make it clear that these are “temporary” and not list them as valid versions when they are not active.

#### Release distributions are smoke tested

The actual distributions that we should must be smoke tested to gain some level of confidence that nothing silly happened.

#### Release process itself is tested

For example, after a new release we should have confidence that the appropriate things have happened. For example, the current version on the website and version service should have been updated.

### Nice to have

The following aspects would be nice to have:

#### Change from a 'release' to 'promote' approach

Currently, we arbitrarily cut a release of the codebase at a point in time. A promotion approach would change this be based around the idea of promoting a build which implies that we are promoting a QA'd change.

#### Separate build/test & promotion phases

This would enable us to promote any particular build (i.e. change that passed QA) as any particular version. It would also allow us to decouple the promotion mechanics from the build/test mechanics giving us increased flexibility and removing some akwardness around versioning.

## Proposed Design

### The build pipeline

The build pipeline builds, tests and analyses (e.g. performance metrics) a change to the codebase.

Changes are made to the codebase. Each change travels through the pipeline, at least to the end of the QA stage. Changes can then selectively be chosen to be promoted in some fashion.

At a high level the pipeline will have the following sequential phases:

#### Phase #1 (Fast Verify)

Performs:
 
* Compilation
* Static Analysis
* Unit Testing
* In Process Integration Tests (i.e. fast mode)

Note: The unit testing and fast integration testing will involve executing on a select, small, number of environments.

#### Phase #2 (Package)

Builds the distribution, verifying that this can be done.

#### Phase 3 (QA)

Runs additional QA such as forking based integration tests (using the package from Phase #2), performance tests etc.

This could be arranged in a number of ways. The details of which are not important for the purpose of this document. 

Part of this is ensuring that we have sufficient QA for both branches; `master` and `release`.

#### Regarding Linux and Windows environments

While the above phases imply a completely linear structure, this is not strictly the case. The “verify” and “QA” phases are swimlaned via platform.

The following diagram should illustrate the intention:

<img src="https://github.com/gradle/gradle/raw/master/design-docs/img/build_pipeline.jpg" />

### The promotion pipeline

The promotion pipeline, takes a change that has “passed” through the build pipeline and promotes it with a symbolic version number. Initially, there will be 4 kinds of promotions:

1. Promote as a snapshot
2. Promote as the latest nightly snapshot (i.e. bleeding edge release)
3. Promote as a release candidate
4. Promote as a final release

A change is considered to have “passed” the build pipeline if it successfully passed phase #2 of the build pipeline. That is, it has passed the fast tests and was able to be packaged into a distribution. For certain kinds of promotions, only changes that have passed the full QA phase of the build pipeline should be promoted, but this will be a social convention rather than a rigid aspect of the automation.

Nightlies will be promoted from the `master` branch. All other promotions will happen from the `release` branch.

At a high level, the promotion pipeline will have the following phases:

#### Phase #1 (Package & Smoke Test)

The distribution(s) will be rebuilt with the allocated symbolic version number. The distributions will then be smoke tested.

#### Phase #2 (Deploy/Release)

The distributions will then be “released”. This means making them available for downloading and triggering any necessary automation, such as updating the website and version information services.

#### Phase #3 (Post Test)

After a deployment/release has been performed, checks will run that the expected outcomes have occurred. For example, is the release being advertised correctly on the website? Can it be downloaded.

### Separating build & promotion

The Gradle project itself will be changed to have all release/promotion aspects removed from it. This build will just be responsible for building/testing a Gradle distribution. Essentially, it concerned with the build pipeline.

A new promotion project will be created that can build the Gradle project, then take the outputs and “promote” them. This project will know how to update the website and perform any other “release automation” as well. Essentially, it is concerned with the promotion pipeline.

### Versioning changes

The Gradle build itself will no longer be concerned with symbolic (i.e. marketing) version numbers. That is, it has no knowledge of numbers such as `1.0` or `1.1`. As far as it is concerned, the public version “number” is arbitrary and can be externally specified (e.g. -PbuildVersion=1.1)

1. If a version number is not specified, a “constant” version number (e.g. 'HEAD') will be used.
2. For builds that are happening as part of the build pipeline, a version number that identifies the build pipeline will be used.
3. For builds that are happeing as part of promotion, a symbolic version number will be used. 

In all cases, extra version metadata will be captured and stored in the distribution. This will include things like build time, git branch name, git commit id etc.

### Release history

Currently, the Gradle source tree contains a `releases.xml` document that describes the release history. Under the new scheme, this record will only exist in the website repository. The promotion build will modify this as part of the website repository as needed.

## Implementation Plan

The implementation will be phased. 

### Phase 1 (introduce promotion project/builds)

The promotion/release logic will be extracted from the Gradle project source tree and moved to a new project. This project will be able to checkout the Gradle project at a particular revision, build it with a certain version number, create a tag of the Gradle project with the version number, then release it.

CI builds will be setup perform this for the 4 different types of promotion outlined in the section in this document titled “The promotion pipeline”.

### Phase 2 (distribution smoke testing)

Adding basic smoke testing the promotion process.

### Phase 3 (post promotion checks)

Adding post checks to the promotion process to verify that the automation worked successfully.

### Phase 4 (build pipeline implementation)

Implementing the build pipeline, as outlined earlier. 