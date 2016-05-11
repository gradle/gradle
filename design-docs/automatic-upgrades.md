Provide the ability to automatically update the wrapper with varying granularity of releases.

## Motivation

Currently, users have to manually upgrade the Gradle wrapper (and thus the version of Gradle they run)
using a `Wrapper` task. Motivation here is to make it easier to proactively upgrade when one is available.

It is a strategic move for Gradle to have users to upgrade more frequently, and this is a manifestation of that strategy. 
It affords the following benefits:
 
 * Users taking advantage of new features we're building becomes easier if they're on later versions (assumption that a smaller version jump is easier than a larger one)
    * For example, this may be especially advantageous if we are building new metrics for Gradle.com to collect then it makes Gradle.com all the more useful
 * Strategic advantage other build tools that don't provide this
 * Users automatically get performance improvements and there is opportunity for pleasant surprise
 * Ability to deprecate features faster may be slightly improved
 
The vast majority of users will want the latest release that doesn't break their builds, we also want to make
it trivial for users to upgrade to RC releases and nightly releases for those teams who closely follow Gradle
development (Open-source partners).

## Risks

* Adherence to our [Backward Compatibility](https://docs.gradle.org/current/userguide/feature_lifecycle.html#backwards_compatibility) principles
must be strictly adhered to in order to avoid "Gradle" itself breaking people's builds. 
 * This may not be as simple as adhering to same major version. If there is significant risk for major version upgrades, we could consider only
   "unstable" distribution channels like CANARY or RC where the expectation of stability is lower.
* Performance impact of checking for updates often would compromise our efforts to improve startup time or a "snappy feel" of Gradle. We should 
consider this in our default behavior here.

## Features

### Feature: Canary wrapper checks for latest nightly and downloads when available

#### Story: As a user, I want to my Gradle wrapper to update itself when there's a new version available 

#### User-facing changes
`Wrapper` task has an additional Input to allow selection of a `DistributionChannel`, abbreviated to `--channel=channelName`.

#### Implementation Plan

* Introduce the concept of an `UpdateCheck` that can retrieve the URI of the latest snapshot/release (from https://services.gradle.org/distributions-snapshots/CHANNEL, perhaps)
* Introduce the concept of a `DistributionChannel` that distinguishes between the following
  * `CANARY` - Latest nightly not marked as `broken: true`. This name is chosen not only because it's a familiar concept 
    for Google Chrome users, but our logo could be adapted with a yellow bird to designate the Canary distribution, 
    which may be favorable for marketing this to users.
  * `RELEASE_CANDIDATE` - Latest RC, agnostic of major version, and stable releases
  * `STABLE_BACKWARD_COMPATIBLE` - Latest compatible release of the same major version release. 2.13 -> 2.14 but 2.14 X> 3.0
  * All other values are rejected, the user is warned and the wrapper executes as if `DistributionChannel` were not configured
* `WrapperConfiguration` allows for designation of a `DistributionChannel` but defaults to None (current behavior as of 2.14)
* `distributionUrl` in Wrapper properties is updated upon successful update

#### Test Cases

* `UpdateCheck` throws sensible errors given invalid JSON/failed HTTP requests to Gradle Services
* Wrapper executes normally if `UpdateCheck` fails

### Story: As a user, I want to be able to configure the frequency of wrapper update checks at a project or system level

#### User-facing changes

* New Gradle Property `org.gradle.wrapper.updatecheckfrequency` set to an Integer value. 
  * A value of `0` would mean "execute `UpdateCheck` on every invocation of the wrapper"
  * A value of `-1` (or any value less than 0) would mean "never execute `UpdateCheck`"
  * A positive Integer would mean "execute `UpdateCheck` if wrapper invocation is after lastCheck + `updatecheckfrequency` value"
  * Default value would be `1_440_000`, or daily.

#### Implementation Plan

* If `DistributionChannel` is configured in `WrapperConfiguration` and `org.gradle.wrapper.updatecheckfrequency` is non-negative, `WrapperExecutor` attempts to read last check timestamp from `WrapperConfiguration`
  * Given invalid or missing lastCheckTimestamp, an `UpdateCheck` is executed
  * Given valid lastCheckTimestamp + `updatecheckfrequency` <= current time (as provided by `TimeProvider`), an `UpdateCheck` is executed
  * Otherwise `UpdateCheck` is not executed
* `lastCheckTimestamp` is written to _wrapper.properties_ file

#### Test cases

* `WrapperExecutor` performs `UpdateCheck` given invalid lastCheckTimestamp
* Verify behavior in implementation regarding when `UpdateCheck` is executed or not
* _wrapper.properties_ is regenerated correctly with `lastCheckTimestamp` if it is missing
