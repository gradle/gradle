This feature is really a bucket for key things we want to fix in the short-term for Dependency Management, many of which have require
(or have the potential for) a strategic solution.

As this 'feature' is a list of bug fixes, this feature spec will not follow the usual template.

## Contents
* [Invalid checksum files generated on publish](#invalid-checksum-files)
* [File stores handle process crash when adding file to store](#crash-safe-file-store)
* [Errors writing cached module descriptor are silently ignored](#errors-on-descriptor-write)
* [Honor SSL system properties when accessing HTTP repositories](#ssl-system-properties)
* [Ignore cached missing module entry when module is missing for all repositories](#cached-missing-modules)
* [Correctness issues in HTTP resource caching](#http-resource-caching)
* [Inform the user about why a module is considered broken](#error-reporting)
* [Correct handling of packaging and dependency type declared in poms](#pom-packaging)
* [RedHat finishes porting gradle to fedora](#fedora)
* [Allow resolution of java-source and javadoc types from maven repositories](#maven-types)
* [Expiring of changing module artifacts from cache is inadequate in some cases, overly aggressive in others](#changing-module-caching)
* [Correct naming of resolved native binaries](#native-binaries)
* [Handle pom-only modules in mavenLocal](#pom-only-modules)
* [Support for kerberos and custom authentication](#authentication)

<a href="invalid-checksum-files">
# Invalid checksum files generated on publish

SHA-1 checksums should be 40 hex characters long. When publishing, Gradle generates a checksum string that does not include leading zeros, so
that sometimes the checksum is shorter than 40 characters.

See [GRADLE-2456](http://issues.gradle.org/browse/GRADLE-2456)

### Test coverage

* Publish an artifact containing the following bytes: [0, 0, 0, 5]. This has an SHA-1 that is 38 hex characters long.
* Assert that the published SHA-1 file contains exactly the following 40 characters: 00e14c6ef59816760e2c9b5a57157e8ac9de4012
* Test the above for Ivy and Maven publication.

### Implementation strategy

* Change DefaultExternalResourceRepository to include leading '0's.
* Change DefaultExternalResourceRepository to encode the SHA1 file's content using US-ASCII.

<a href="crash-safe-file-store">
# File stores handle process crash when adding file to store

When Gradle crashes after writing to a `FileStore` implementation, it can leave a partially written file behind. Subsequent invocations of Gradle
will use this partial file.

See [GRADLE-2457](http://issues.gradle.org/browse/GRADLE-2457)

### Test coverage

No specific coverage at this point, other than unit testing. At some point we'll set up a stress test.

### Implementation strategy

Something like this:

* Add IndexableFileStore<K> interface with a single FileStoreEntry get(K key) method.
* Change PathKeyFileStore to implement this method.
* Add FileStore.add(Action<File> addAction) method. The action is given a file that it should write the contents to. This initial implementation would
  basically do:
    1. Allocate a temp file using getTempfile()
    2. Call Action.execute(tempfile) to create the file
    3. If the action is successful, call move(key, tempfile) to move the temp file into place.
* Change ModuleDescriptorStore and/or ModuleDescriptorFileStore to use a PathKeyFileStore to manage access to the actual file store.
* Change DownloadingRepositoryCacheManager to use FileStore.add() instead of FileStore.getTempfile() and move().
* Remove FileStore.getTempfile().
* Change the implementation of PathKeyFileStore.copy(), move() and add() so that it:
    1. Places a marker file down next to the destination file.
    2. Calls Action.execute(destfile)
    3. If successful, removes the marker file.
    4. If fails, removes the marker file and the destination file.
    5. Maybe also add some handling to File.move() the original destination file out of the way in step 1, and back in on failure in step 4.
* Change PathKeyFileStore.get() and search() to ignore and/or remove destination files for which a marker file exists.

<a href="errors-on-descriptor-write">
# Errors writing cached module descriptor are silently ignored

See [GRADLE-2458](http://issues.gradle.org/browse/GRADLE-2458)

### Test coverage

No specific coverage at this point, other than unit testing.

### Implementation strategy

* Copy XmlModuleDescriptorWriter, add some unit tests.
* Fix XmlModuleDescriptorWriter so that it does not ignore errors.
* Change ModuleDescriptorStore and IvyBackedArtifactPublisher to use this to write the descriptors.

<a href="ssl-system-properties">
# Honor SSL system properties when accessing HTTP repositories

See [GRADLE-2234](http://issues.gradle.org/browse/GRADLE-2234)

### Test coverage

* Can resolve dependencies from an HTTPS Maven repository with both server and client authentication enabled.
    * Both an SSL trust store containing the server cert and a key store containing the client cert have been specified using the `ssl.*`
      system properties.
    * Assert that expected client cert has been received by the server.
* Can publish dependencies to an HTTPS Maven repository with both server and client authentication enabled, as above.
* Resolution from an HTTP Maven repository fails with a decent error message when client fails to authenticate the server (eg no trust store specified).
* Resolution from an HTTP Maven repository fails with a decent error message when server fails to authenticate the client (eg no key store specified).
* Same happy day tests for an HTTP Ivy repository.

### Implementation strategy

Needs some research. Looks like we need should be using a `DecompressingHttpClient` chained in front of a `SystemDefaultHttpClient` instead of
using a `ContentEncodingHttpClient`.

<a href="cached-missing-modules">
# Ignore cached missing module entry when module is missing for all repositories

Currently, we cache the fact that a module is missing from a given repository. This is to avoid a remote lookup when a resolution uses multiple repositories,
and a given module is not hosted in every repository.

This causes problems when the module is initially missing from every repository and subsequently becomes available. For example, when setting up a new
repository, you may misconfigure the repository server so that a certain module is not visible. Gradle resolves against the repository and caches the fact that
the module is missing from that repository, and reports the problem to you. You then fix the server configuration so that the module is now visible. When
Gradle resolves a second time, it uses the cached value, instead of checking for the module.

### Integration test coverage

* Multiple repositories:
    1. Resolve with multiple repositories and missing module.
    2. Assert build fails due to missing module.
    3. Publish the module to the second repository.
    4. Resolve again.
    5. Assert Gradle performs HTTP requests to look for the module.
    6. Assert build succeeds.
    7. Resolve again.
    8. Assert Gradle does not make any HTTP requests
    9. Assert build succeeds.

### Implementation strategy

When resolving a dependency descriptor in `UserResolverChain` for a particular repository:

1. If there is a cached 'found' entry for this repository and the entry has not expired, use the cached value.
2. If there is a cached 'not-found' entry for this repository and the entry has not expired, and there is some other repository for which there is an
   unexpired 'found' entry, use the cached value.
3. Otherwise, resolve the dependency using this repository.

<a href="http-resource-caching">
# Correctness issues in HTTP resource caching

* GRADLE-2328 - invalidate cached HTTP/HTTPS resource when user credentials change.
* Invalidate cached HTTP/HTTPS resource when proxy settings change.
* Invalidate cached HTTPS resource when SSL settings change.

### Integration test coverage

TBD

### Implementation strategy

TBD

<a href="error-reporting">
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

<a href="pom-packaging">
# Correct handling of packaging and dependency type declared in poms

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

* When the dependency declaration has no 'type' specified, or a 'type' that maps to the extension 'jar'
    * Resolution of a POM module with packaging in ['', 'pom', 'jar', 'ejb', 'bundle', 'maven-plugin', 'eclipse-plugin'] will not change
    * Resolution of a POM with packaging 'foo' that maps to 'module.foo', a deprecation warning will be emitted and the artifact 'module.foo' will be used
    * Resolution of a POM with packaging 'foo' that maps to 'module.jar', the artifact 'module.jar' will be successfully found. (ie 'orbit'). An extra HTTP
      request will be required to first look for 'module.foo'.
* When the dependency declaration has a 'type' specified that maps to an extension 'ext' (other than 'jar')
    * Resolution of a POM module with packaging in ['pom', 'jar', 'ejb', 'bundle', 'maven-plugin', 'eclipse-plugin'] will emit a deprecation warning before using 'module.jar' if it exists
    * Resolution of a POM with packaging 'foo' and actual artifact 'module.foo', a deprecation warning will be emitted and the artifact 'module.foo' will be used
    * Resolution of a POM with packaging 'foo' and actual artifact 'module.ext', the artifact 'module.ext' will be successfully found. An extra HTTP
      request will be required to first look for 'module.foo'.

### Integration test coverage

* Coverage for resolving pom dependencies referenced in various ways:
    * Need modules published in maven repositories with packaging = ['', 'pom', 'jar', 'war', 'eclipse-plugin', 'custom']
    * Test resolution of artifacts in these modules via
        1. Direct dependency in a Gradle project
        2. Transitive dependency in a maven module (pom) which is itself a dependency of a Gradle project
        3. Transitive dependency in an ivy module (ivy.xml) which is itself a dependency of a Gradle project
    * For 1. and 2., need dependency declaration with and without type attribute specified
    * Must verify that deprecation warning is logged appropriately
* Sad-day coverage for the case where neither packaging nor type can successfully locate the maven artifact. Error message should report 'type'-based location.

### Implementation approach

* Determine 2 locations for the primary artifact:
    * The 'packaging' location: apply the current logic to determine location from module packaging attribute
        * Retain current packaging->extension mapping for specific packaging types
    * The 'type' location: Use maven3 rules to map type->extension+classifier, and construct a location
* If both locations are the same, use the artifact at that location.
* If not, look for the artifact in the packaging location
    * If found, emit a deprecation warning and use that location
    * If not found, use the artifact from the type location
* In 2.0, we will remove the packaging->extension mapping and the deprecation warning

<a href="fedora">
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

<a href="maven-types">
# Allow resolution of java-source and javadoc types from maven repositories (and other types: tests, ejb-client)

* GRADLE-201: Enable support for retrieving source artifacts of a module
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

<a href="changing-module-caching">
# Expiring of changing module artifacts from cache is inadequate in some cases, overly aggressive in others

* GRADLE-2175: Snapshot dependencies with sources/test classifier are not considered 'changing'
* GRADLE-2364: Cannot run build with --offline after attempting build with a broken repository

### Description

When it's time to update a changing module, we expire the module descriptor and all artifact entries from the cache. The mechanism to do so it quite naive:

1. We lookup the module descriptor in the cache for the repository, and determine if it needs updating (should be expired)
2. If so, we iterate over all artifacts declared by the module descriptor and remove the cache entry for each artifact. The artifact files remain, but the repository reference is removed.
3. When resolving artifacts we do not consider if the owning module is changing or not, we assume if the artifact is available then it is up-to-date.

This leads to a couple of issues:
* Sometimes the module descriptor does not declare all of the artifacts that have been cached (eg source/javadoc artifacts). These are then not expired, and will never be updated (GRADLE-2175).
* If we fail to resolve the changing module (eg broken repository) then we have already removed the module+artifacts from the cache, so they are not available for --offline builds. (GRADLE-2364)

### Strategic solution

Once we've found a module in the cache, we should not need to search the cache for each artifact that is attached to that module. Instead, we could store a reference to each
cached artifact with the cached module. Then when we are resolving the artifact file we would lookup the cached module and use that to locate the cached artifact file,
instead of searching for the artifact file directly. When an artifact file is downloaded, the cache entry for that file would be attached to the cached module descriptor, rather
than referencing the cached artifact as a separate entity.

Doing this would allow a cached module to be managed as a single unit. Removing the cached module would automatically remove all cached artifact references, and it would be easy
to delay this removal until the module was successfully resolved from a repository.

Taking this a little further, if the ModuleDescriptor returned from the cache was able to hold some reference to it's cached entry, it could be used directly when resolving artifact
files, rather than requiring that it be looked up again for each artifact. This would require we switch from using org.apache.ivy.ModuleDescriptor as our internal module representation,
to use a richer module model that would allow us to retain cache references throughout the resolve process.

As this change would require an update to the cache storage layout, this might be a good opportunity to separate the Gradle filestore (non-binary, stable) from the rest of the
cache storage (binary, unstable).

### User-visible changes and backward-compatibility issues

* Beside the bugfixes for changing module caching, no user-visible change to cache behaviour.
* New cached module format means updated cache version, requiring artifacts to be re-downloaded where no SHA1 keys are published.
* Since org.apache.ivy.ModuleDescriptor is not part of our public API (except via ivy DependencyResolver), any change to using this internally should not impact users.

### Integration test coverage

* Will download changed version of ${name}-${classifier}.${ext} of changing module referenced with classifier
* Will download changed version of source jar of changing module referenced with type="source"
    * Verify that we will not download unchanged version of changing module
* Verify that we recover from failed resolution of module after initial successful resolution
    * Failure cases are authorization error (401), server error (500) and connection exception
    * Will re-attempt download on subsequent resolve and recover
    * Will use previously cached version if run with --offline after failure

### Implementation approach

* The goal is to have the current behaviour of ModuleDescriptorCache and ArtifactAtRepositoryCachedExternalResourceIndex operate atomically, so that removing/expiring a module from the cache automatically
removes/expires all artifacts linked to that module. One option is to update the ModuleDescriptorCache so that it is able to return the full CacheExternalResource for any artifacts that have been
 previously resolved for a particular module. This would mean replacing/wrapping the ArtifactAtRepositoryCachedExternalResourceIndex with calls to the ModuleDescriptorCache.
 The key entry point method is CachingModuleVersionRepository.download(Artifact).
* Split the filestore out of the artifacts-N directory: introduce metadata-1 and filestore-1 as the 2 versioned artifact storage directories. The metadata-N directory will store binary artifact files,
and will require a new version whenever the binary storage format is changed. The filestore-N directory will store downloaded files in a pattern that encapsulates the artifact identifier and the SHA1 checksum
of the downloaded artifact (same as current format).

<a href="native-binaries">
# Correct naming of resolved native binaries

* GRADLE-2211: Resolved binary executables and libraries do not use the platform specific naming scheme

<a href="pom-only-modules">
# Handle pom-only modules in mavenLocal

* GRADLE-2034: Existence of pom file requires that declared artifacts can be found in the same repository
* GRADLE-2369: Dependency resolution fails for mavenLocal(), mavenCentral() if artifact partially in mavenLocal()

<a href="authentication">
# Support for kerberos and custom authentication

* GRADLE-2335: Provide the ability to implement a custom HTTP authentication scheme for repository access

# Done

## Project dependencies in generated poms use correct artifactIds

* GRADLE-443: Generated POM dependencies do not respect custom artifactIds
* GRADLE-1135: Gradle generates the wrong artifactId in the pom.xml for project dependencies in multi project builds

### Description

When we generate a POM (or ivy.xml) for a project that has project dependencies, the POM needs to declare dependencies on the published artifacts of those depended projects.
Currently, these dependencies in the generated pom always use the ${project.name} value as the name of the artifact in the depended project: however this is incorrect
if the artifact name of the depended project has been modified by:

* Changing the artifactId via the mavenPom attached to a MavenDeployer
* Changing the artifactId (maven) or module (ivy) via the archivesBaseName
* Changing the name of the generated archive using the baseName parameter of the jar task (actually MavenDeployer seems to ignore this value)

This is further complicated by the fact that a single project can generate multiple archives (with different POM files and artifact ids). We need to pick one (or more)
of the POM files to reference in the case of a project dependency.

### Strategic solution

The end-goal would be to replace the use of MavenDeployer with our nascent publication DSL.

However, a useful step would be to simply add some integration test coverage for multi-project publish/consume,
and fix the bug for the case where the module artifact is renamed via archivesBaseName.

### User-visible changes and backward-compatibility issues

When archivesBaseName is specified for a project, the published artifacts will use that value when creating a POM artifactId. And when a project dependency is made to
such a project, generated POM dependencies will also use the same artifactId.

If the artifactId is specified directly on the POM within the MavenDeployer, we would continue to publish with the explicit artifactId. Since this would not modify the artifact
model of the project, there would still be no convenient way to reference this artifact as a project dependency.

### Integration test coverage

* Publication of an ivy project with multiple artifacts for a single configuration
* Publication of an ivy project with multiple artifacts in separate configurations
* Multi-module build with projectA publishing and projectB having a dependency on projectA. For each case check resolution, as well as published metadata for projectA & projectB.
Try maven publication for projectA & projectB as well as ivy publication for projectA & projectB. Possibly a combination?
    * With no modification to publication coordinates: should work for maven & ivy
    * projectA with modified archivesBaseName: will fail for Maven (GRADLE-443)
    * projectA with additional artifact given classifier: should work for maven & ivy
    * projectA with additional artifact given different name: should work for ivy, not possible in maven
    * projectA with multiple publish configurations: projectB depends on 1 or both
* MavenPublishRespectsPomConfigurationTest exposes the bug, but this proposal will not solve that exact use case: modifying published artifactId directly on POM. Should remove/update
this ignored test.

### Implementation approach

* Add an experimental extension to project that provides the publish coordinates for a project. The publish coordinates for a non-maven project will be the group:projectId:version.
    * At a later point we'll add publish coordinates per-configuration, but for now per-project will suffice.
* The maven plugin will update the publish coordinates to be group:archivesBaseName:version
    * MavenDeployer already uses archivesBaseName when publishing, so no change needed to publication
* Modify PublishModuleDescriptorConverter (ProjectDependencyDescriptorFactory) so that it uses the publish coordinates of a project when creating a published descriptor for a project dependency.

## Dynamic versions work with authenticated repositories

* GRADLE-2318: Repository credentials not used when resolving dynamic versions from an Ivy repository
* GRADLE-2199: IvyArtifactRepository does not handle dynamic artifact versions

### Description

We are using org.apache.ivy.util.url.ApacheURLLister to obtain a list of versions from a directory listing, and this code is not using the supplied credentials.
Thus dependency resolution fails for dynamic versions with and authenticated ivy repository.

### Strategic solution

We should take the opportunity to remove a bit more ivy code from our dependency resolution, and to handle listing of available versions in a more consistent manner
between ivy/maven and http/filesystem. Longer term it may be useful to be able to recombine these: eg. maven-metadata.xml for listing versions with ivy.xml for module descriptor.

### User visible changes

* Dynamic versions resolved against an authenticated ivy repository will use configured credentials for that repository.
* If the repository returns a 404 for the directory listing, we treat the module as missing from that repository
* If the resolve is unable to list the versions of a module within a repository for any other reason, we treat the repository as broken for that module.
A warning will be emitted and that repository will be skipped and resolve will continue with next repository. Broken response is not cached.
    * If the user does not have credentials + permissions to list directory content.
    * If the directory listing request results in an HTTP failure (500, Exception).
    * If the directory listing response cannot be parsed to extract the module versions.

### Integration test coverage

* Happy-day tests
    * Resolve dynamic version against an authenticated ivy repository
    * Resolve a dynamic version and SNAPSHOT (unique & non-unique) against an authenticated maven repository
    * Resolve a dynamic version against 2 repositories, where the first one does not contain the module (returns 404 for directory listing).
* Sad-day tests: when module is considered broken in repository, repository is skipped and warning is emitted. Integ test should demonstrate that result is not cached, so repository
  will recover on subsequent resolve.
    * No credentials supplied (401 unauthorized)
    * 500 response from server
* Unit-test coverage for 'broken' module (verify that appropriate exception is thrown):
    * No credentials supplied.
    * Invalid credentials supplied.
    * User has permissions to get individual artefacts, but does not have permission to list directories.
    * We get a 500 or other unexpected response for the directory list HTTP request.
    * We get a response with an entity of an unknown content type.
    * We get a response whose entity we cannot parse.
    * We get a ConnectException or other exception for the directory list HTTP request.
* Extending existing coverage
    * Happy-day test: Resolve a Maven version range (declared as [1.0, 2.0) in a POM and referenced transitively) against a (non-authenticated) maven repository

### Implementation approach

Replace the existing HTTP implementation of ExternalResourceLister so that it doesn't use ApacheURLLister. It should instead be backed by a ExternalResourceAccessor
to access the directory listing resource, with the code for parsing the Apache directory listing result adopted from ApacheURLLister.

At a higher abstraction layer, we could introduce an API that is able to list all available versions for a module; this would replace the current use of ResolverHelper in ExternalResourceResolver.
for now we'll need an implementation backed by ExternalResourceRepository#list (hence ExternalResourceLister) and another backed by maven-metadata.xml.

Later we may add a ModuleVersionLister backed by an Artifactory REST listing, our own file format, etc... The idea would be that these ModuleVersionLister
implementations will be pluggable in the future, so you could combine maven-metadata.xml with ivy.xml in a single repository, for example.
