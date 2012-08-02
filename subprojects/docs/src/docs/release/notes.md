Our documentation has received a facelift to match our new style. Check out the new look [DSL Reference](dsl/index.html) and [User Guide](userguide/userguide.html).

## New and noteworthy

Here are the new features introduced in Gradle 1.2.

## Upgrading from Gradle 1.1

Please let us know if you encounter any issues during the upgrade to Gradle 1.2, that are not listed below.

### Deprecations

<a name="constructors"> </a>
#### Plugin constructor handling

As a first step towards handling JSR-330 style dependency injection for plugin implementations, we have made some changes to how plugin constructors
are handled by Gradle. These changes are fully backwards compatible, but some combinations of constructors are now deprecated.

If your plugin implementation has exactly one default constructor, nothing has changed. This should be the case for the majority of plugins.

If your plugin implementation has multiple constructors, you will need to add an @javax.inject.Inject annotation to the default constructor. The implementation will continue to work
without this, but you will receive a deprecation warning. In Gradle 2.0, a plugin implementation with multiple constructors will be required to annotated exactly one constructor
with an @Inject annotation.

### Potential breaking changes

See [Plugin constructor handling](#constructors) above. The changes should be backwards compatible. Please let us know if you come across a situation where
a plugin implementation that worked with previous versions of Gradle does not work with Gradle 1.2.

## Fixed Issues

The list of issues fixed between 1.1 and 1.2 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.2-rc-1%22%29+ORDER+BY+priority&tempMax=1000).
