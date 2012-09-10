## New and noteworthy

Here are the new features introduced in Gradle 1.3.

## Promoted features

Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to our backwards compatibility policy.

## Fixed Issues

The list of issues fixed between 1.2 and 1.3 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.3-rc-1%22%29+ORDER+BY+priority&tempMax=1000).

## Incubating features

We will typically introduce new features as _incubating_ at first, giving you a chance to test them out.
Typically the implementation quality of the new features is already good but the API might still change with the next release based on the feedback we receive.
For some very challenging engineering problems like the Gradle Daemon or parallel builds, it is impossible to get the implementation quality right from the beginning.
So we need you here also as beta testers.
We will iterate on the new feature based on your feedback, eventually releasing it as stable and production-ready.
Those of you who use new features before that point gain the competitive advantage of early access to new functionality in exchange for helping refine it over time.
To learn more read our [forum posting on our release approach](http://forums.gradle.org/gradle/topics/the_gradle_release_approach).

### Resolution result API

* (in progress)
* The entry point to the ResolutionResult API has changed, you can get access to the instance of the ResolutionResult from the ResolvableDependencies.

## Upgrading from Gradle 1.2

Please let us know if you encounter any issues during the upgrade to Gradle 1.3, that are not listed below.

### Deprecations

### Potential breaking changes
