This feature is really a bucket for key things we want to fix in the short-term for Dependency Management, many of which have require
(or have the potential for) a strategic solution.

As this 'feature' is a list of bug fixes, this feature spec will not follow the usual template.

# Conflict resolution considers conflicts on production classes

See [this post](http://forums.gradle.org/gradle/topics/npe_in_dependencygraphbuilder_dependencyedge_getfailure)
See [GRADLE-2752](https://issues.gradle.org/browse/GRADLE-2752)

- Allow self-resolving dependencies to imply zero or more module versions.
- Change the resolution algorithm to consider this during conflict resolution. Self-resolving dependencies cannot be evicted and must
  always be selected. Assert that the self-resolving dependency is compatible-with the requested version. Fail resolution if not.
- Change the Java plugin to attach current project's identifier to the main and test classes.
    - To infer the identifier of the current project.
        - Identifier of any publication.
        - Project (group, name, version).
        - Maven deployer.
        - Project (group, archivesBaseName, version).
- Change the resolution algorithm so that the root node is not considered by conflict resolution.

TBD - A later story should travel back from each publication to determine if the main or test binary is included in the publication.
Use this publication's identifier if so.

# Conflict resolution considers local dependencies

See [GRADLE-2516](https://issues.gradle.org/browse/GRADLE-2516)

- Change the local dependencies (eg `gradleApi()`, `localGroovy()`) to imply the various modules that they contribute to the result.

# Correctness issues in HTTP resource caching

* GRADLE-2328 - invalidate cached HTTP/HTTPS resource when user credentials change.
* Invalidate cached HTTP/HTTPS resource when proxy settings change.
* Invalidate cached HTTPS resource when SSL settings change.

### Integration test coverage

TBD

### Implementation strategy

TBD

# Inform the user about why a module is considered broken

### Description

There is currently no consistent approach to exception handling or reporting in our dependency resolution code. Sometimes failures are ignored, sometimes they are
reported as a 'not found', and sometimes they bubble up and break resolution, even when the module is available at another repository.

### Strategic solution

There are 3 goals:

* Add a couple of key firewalls in the code, where we handle failures and make a high-level decision whether to proceed (and warn), or fail the resolution
(and add some context).
* Remove all known places in our (inherited) code where we catch and ignore, catch and mark as unknown, or catch and discard the cause.
* Wrap/change each parser to add context on parse failure.

### User visible changes

There are 4 possible outcomes when resolving a module or downloading an artifact from a given repository:

* Success
* A remote resource is not found (a 404 status code for an HTTP request, or empty directory listing).
* An expected failure occurs accessing a remote resource (Connect exception, a non-200 status code, or a parse error).
* An unexpected failure occurs (all other failures).

In general, when an expected or unexpected failure occurs resolving from a repository:
1. The resolve is aborted for that repository (this means, for example, if we can't parse a .sha1 resource, then we bail).
2. If the failure is an unexpected failure, then propogate it and fail the resolve.
3. Log the details of the failure.
4. If there are no other repositories defined, then fail the resolve.
5. If there is another repository defined, proceed to the next repository.

TODO - there are some exceptions to this rule, for example:
* if we get a 401 fetching the .sha1, we should continue to the artifact
* if we get a 401 fetching an artifact, and the repository has multiple root URLs, we should continue on to the next URL.

### Integration test coverage

TODO - flesh this out

### Implementation approach

TODO - flesh this out

# RedHat finishes porting gradle to fedora

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
* repo location defined in settings.xml in M2_HOME/conf
* repo location defined in ~/.m2/settings.xml and in M2_HOME (settings file precedence) 
And sad-day tests for 'resolve from mavenLocal':
* repo directory does not exist
* settings.xml file is invalid

And a test that regular resolve succeeds from http repository when settings.xml is invalid. The local artifact reuse stuff tries to find candidates in mavenLocal.

### Implementation approach

* Implement all of the integration tests
* Implement m2 repository location with Maven3
* Use jarjar to repackage the required maven3 classes and include them in the Gradle distro.

# Allow resolution of java-source and javadoc types from maven repositories (and other types: tests, ejb-client)

* GRADLE-1444: Sources are not downloaded when dependency is using a classifier
* GRADLE-2320: Support for multiple artifacts with source jars in Eclipse plugin

### Description

Maven plugins publish various artifact 'types' using well-known naming schemes.

* The maven-source-plugin packages sources for a project into ${name}-sources.jar, and test sources into ${name}-test-sources.jar.
* The maven-javadoc-plugin packages javadoc for a project into ${name}-javadoc.jar and test code into ${name}-test-javadoc.jar.
* The maven-ejb-plugin packages the client jar into ${name}-client.jar. This plugin allows dependencies with type='ejb' to reference the standard jar file,
and dependencies with type='ejb-client' to reference the client jar.
* The maven-jar plugin creates ${name}-tests.jar when run with the jar:test-jar plugin. This guide: http://maven.apache.org/guides/mini/guide-attached-tests.html states that the
recommended way to reference the test jar as a dependency is by using type='test-jar'. Using classifier='tests' may also work, but has issues with some other plugins.

An example of a module containing a bunch of permutations is http://repo1.maven.org/maven2/org/codehaus/httpcache4j/httpcache4j-core/2.2/

Currently, the only way to resolve 'source' or 'javadoc' artifacts for a module is by using well-known classifiers ('sources' & 'javadoc'). This means that you need
to explicitly model these as dependency artifacts, or programmatically add these artifacts (with classifiers) to a detached configuration (see IdeDependenciesExtractor).

Currently, the only way to resolve 'tests' or 'ejb-client' artifacts for a module is by using well-known classifiers.

### Strategic solution

This would be a good opportunity to introduce some stronger typing to our dependency model, and to map to/from the maven model for these artifact/dependency types.
The proposed model for dependencies is outlined in ./dependency-model.md. A good start would be to explicitly map 'source' and 'javadoc' artifact types to/from the
maven repository model when resolving.

### User visible changes
An attempt to resolve a dependency of type 'source' with no classifier would map to the ${name}-sources.${ext} in the maven repository.
If a classifier other than 'sources' was on the artifact, then we would try to locate the artifact at ${name}-${classifier}-sources.${ext}.
For backward compatibility, we will continue to honour the current model of locating an artifact of type 'source' with classifier!='sources' via the
${name}-${classifier}.${ext} pattern, but we should emit a deprecation warning for this behaviour.
\
When we reach 2.0, we could:
i) remove the support for 'source' artifacts with a pattern other than ${name}-${classifier}-sources.${ext}
ii) stop adding the 'sources' classifier to the Gradle model of artifacts with type='source'.

We can apply similar changes for artifacts of type 'javadoc', 'test-jar' and 'ejb-client' for maven repositories

### Integration test coverage

* Coverage for resolving typed dependencies on maven modules referenced in various ways:
    * Need module published in maven repository with various 'classifier' artifacts:  ['source', 'javadoc', 'client', 'test-jar']
    * Test resolution of artifacts in these modules via
        1. Direct dependency in a Gradle project with relevant type specified
        2. Transitive dependency in a maven module (pom) which is itself a dependency of a Gradle project
        3. Transitive dependency in an ivy module (ivy.xml) which is itself a dependency of a Gradle project
* Coverage for maven module published with "${name}-src.jar" pattern: this will require use of classifiers to resolve, and should emit deprecation warning.
* Sad-day coverage for the case where neither type nor classifier can successfully locate the maven artifact. Error message should report 'type'-based location as expected.

### Implementation approach

This will mean that we will be checking 2 locations for such an
artifact: ${name}-${classifier}.${ext} and ${name}-${classifier}-sources.${ext}. An example where the former is required would be classifier='src', for the latter
classifier='jdk15' (where jdk15 has a different source jar).

* For an artifact of type "source", construct 2 possible locations for the artifact:
    1. The 'classifier' location: ${name}-${classifier}.${ext}
    2. The 'type' location: ${name}-sources.${ext}
* If both locations are the same, use the artifact at that location.
* If not, look for the artifact in the 'classifier' location
    * If found, emit a deprecation warning and use that location
    * If not found, use the artifact from the 'type' location
* In 2.0, we will remove the use of the classifier location for 'source' artifacts, and the deprecation warning

Similarly:
* The above changes apply to type="javadoc", classifier="javadoc" and type pattern = "${name}-javadoc.${ext}"
* The above changes apply to type="test-jar", classifier="tests" and type pattern = "${name}-tests.${ext}"
* The above changes apply to type="ejb-client", classifier="client" and type pattern = "${name}-client.${ext}"

It would be good to try to use Maven3 classes to assist with the mapping of [type]->URL and [type,classifier]->URL if possible.

Until we map these types into the ivy repository model as well:
* The IDEDependenciesExtractor will need to continue using type+classifier
* We cannot deprecate the use of classifier='sources'

# Correct naming of resolved native binaries

* GRADLE-2211: Resolved binary executables and libraries do not use the platform specific naming scheme

# Support for kerberos and custom authentication

* GRADLE-2335: Provide the ability to implement a custom HTTP authentication scheme for repository access
