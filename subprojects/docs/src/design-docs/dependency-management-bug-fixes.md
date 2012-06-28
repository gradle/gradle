This feature is really a bucket for key things we want to fix in the short-term for Dependency Management, many of which have require
(or have the potential for) a strategic solution.

As this 'feature' is a list of bug fixes, this feature spec will not follow the usual template.

## Correct handling of packaging and dependency type declared in poms

* GRADLE-2188: Artifact not found resolving dependencies with packaging/type "orbit"

### Description
Our engine for parsing Maven pom files is borrowed from ivy, and assumes the 'packaging' element equals the artifact type, with a few exceptions (ejb, bundle, eclipse-plugin, maven-plugin).
This is different from the way Maven does the calculation, which is:
* Type defaults to 'jar' but can be explicitly declared.
* Maven maps the type to an [extension, classifier] combination using some hardcoded rules. Unknown types are mapped to [type, ""].
* To resolve the artefact, maven looks for an artefact with the given artifactId, version, classifier and extension.

### Strategic solution

At present, our model of an Artifact is heavily based on ivy; for this fix we can introduce the concept of mapping between our internal model and a repository-centric
artifact model. This will be a small step toward an independent Gradle model of artifacts, which then maps to repository specific things link extension, classifier, etc.

### User visible changes

* We should successfully resolve dependencies with packaging = 'orbit'
* We should emit a deprecation warning for cases where packaging->extension mapping does not give same result as type->extension mapping

### Integration test coverage

* Add some more coverage of resolving dependencies with different Maven dependency declarations. See MavenRemotePomResolutionIntegrationTest.
    * packaging = ['', 'pom', 'jar', 'war', 'eclipse-plugin'] ('orbit' can be a unit test)
    * type = ['', 'jar', 'war']

### Implementation approach

* Map type->extension+classifier when resolving from Maven repositories. Base this mapping on the Maven3 rules.
* Retain current packaging->extension mapping for specific packaging types, and add 'orbit' as a new exception to this mapping.
* Emit a deprecation warning where packaging != jar and use of packaging->extension mapping gives a different result to type->extension mapping.
* In 2.0, we will remove the packaging->extension mapping and the deprecation warning

## RedHat finishes porting gradle to fedora

* GRADLE-2210: Migrate to maven 3
* GRADLE-2238: Use maven 3 classes to locate maven local repository
* GRADLE-2366: Have mavenLocal() check M2_HOME/conf/settings.xml
* http://forums.gradle.org/gradle/topics/why\_does\_maven\_deployer\_ignore\_the\_specified_repository
* http://forums.gradle.org/gradle/topics/crash\_when\_use\_gradle\_1\_0\_rc\_1\_on\_mac_osx\_10\_7\_3

### Description

As part of the effort to include Java software in Fedora (http://fedoraproject.org) we are in the process of building and packaging Gradle.
One of the issues we have is that Fedora has a very strict requirement: any software in Fedora should be buildable with software already existing in Fedora.
In order to meet this requirement we are preparing using an ant build script to build a first version of Gradle that we can then to auto-build Gradle.

One of the issues we find with this approach is the following error:
Caused by: org.gradle.api.internal.artifacts.mvnsettings.CannotLocateLocalMavenRepositoryException: java.lang.NoSuchFieldException: userSettingsFile
at org.gradle.api.internal.artifacts.mvnsettings.DefaultLocalMavenRepositoryLocator.buildSettings(DefaultLocalMavenRepositoryLocator.java:75)

The Fedora project already has the Maven3 jars available for use in this build, but not the Maven2 jars that we use in the DefaultLocalMavenRepositoryLocator.

### Strategic solution

While a number of Maven2 classes leak out through the Gradle DSL and cannot be immediately replaced/removed, it is a long-term goal to remove these classes from our public
API. This would allow us to upgrade to use Maven3 classes for various maven/gradle integration points (POM parsing, handling maven-metadata.xml, ...): Maven2 classes are
very often unsuitable for this purpose.

In order to start using Maven3 classes without removing Maven2 from our API, we can try to use JarJar to provide the Maven3 classes under a different namespace. This
would allow us to migrate internally to Maven3; with a goal of deprecating and removing Maven2 for Gradle 2.0.

### User visible changes

No intentional user visible change.

### Integration test coverage

We currently have inadequate coverage for mavenLocal repositories, which depend on the LocalMavenRepositoryLocator that is being converted.

The following 'resolve from mavenLocal' happy-day tests would be useful:
* no settings.xml is defined
* repo location defined in ~/.m2/settings.xml
* repo location defined in settings.xml in M2_HOME
* repo location defined in ~/.m2/settings.xml and in M2_HOME (settings file precedence) 
And sad-day tests for 'resolve from mavenLocal':
* repo directory does not exist
* settings.xml file is invalid

And a test that regular resolve succeeds from http repository when settings.xml is invalid. The local artifact reuse stuff tries to find candidates in mavenLocal.

### Implementation approach

* Implement all of the integration tests
* Implement m2 repository location with Maven3
* Use jarjar to repackage the required maven3 classes and include them in the Gradle distro.

## Dynamic versions work with authenticated repositories

* GRADLE-2318: Repository credentials not used when resolving dynamic versions from an Ivy repository

### Description

We are using org.apache.ivy.util.url.ApacheURLLister to obtain a list of versions from a directory listing, and this code is not using the supplied credentials.
Thus dependency resolution fails for dynamic versions with and authenticated ivy repository.

### Strategic solution

We should take the opportunity to remove a bit more ivy code from our dependency resolution, and to handle listing of available versions in a more consistent manner
between ivy/maven and http/filesystem. Longer term it may be useful to be able to recombine these: eg. maven-metadata.xml for listing versions with ivy.xml for module descriptor.

Currently, the ModuleVersionRepository (wraps ivy DependencyResolver) is responsible for choosing the 'best' of all available versions from a single repository.
Then the DependencyToModuleResolver (UserResolverChain) picks the 'best' version out of the set or repository candidates. Finally the ResolveEngine performs conflict resolution on
the various versions brought in by different dependencies. It would be good to move toward having all of the candidates in the ResolveEngine and choosing the 'best'
in one spot. For this story, we could investigate changing ModuleVersionRepository so that it returns the full set of available versions to the DependencyToModuleResolver
thus removing one of the places where this decision is made.
This would only apply to our own repository implementations, and not to any native Ivy DependencyResolvers.

### User visible changes

Dynamic versions resolved against an authenticated ivy repository will work. No other changes.

### Integration test coverage

* Add test for Maven dynamic version resolution (transitive dependencies with version range) to MavenRemoteDependencyResolutionIntegrationTest
* Add test for resolving ivy dynamic version with authentication to HttpAuthenticationDependencyResolutionIntegrationTest
* Add test for Maven dynamic version and SNAPSHOT resolution with authentication to HttpAuthenticationDependencyResolutionIntegrationTest

### Implementation approach

Remove use of ResolverHelper from ExternalResourceResolver#listVersions. Introduce an API that is able to list available versions for a module,
that can be backed by maven-metadata.xml, a directory listing, a standard Apache directory listing page, an Artifactory REST listing, etc...
Implementations should be backed by ExternalResourceAccessor where possible.

Add a method to ModuleVersionRepository that provides the full list of available versions, and implement this directly in ExternalResourceRepository. Need to investigate
how to handle versions like 'latest.release', that require the full module descriptor in order to choose the 'best'. There is also a 'resolve date' available to
restrict versions based on publication date: I believe this is always null when resolving through Gradle but we would need to confirm.
See ExternalResourceRepository#findResourceUsingPatterns() and in particular #findDynamicResourceUsingPattern().
