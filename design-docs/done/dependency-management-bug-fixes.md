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

### Implementation approach

The goal is to never remove information from the meta-data stores, but instead to replace out-of-date information with its newer equivalent. To achieve
this, we will introduce a synthetic version for changing modules.

1. Increment the cache layout version in `DefaultCacheLockingManager`. Will need to update `LocallyAvailableResourceFinderFactory` for this change.
2. Change `ModuleDescriptorCache.CachedModuleDescriptor` to add a `descriptorHash` property.
3. Change `DefaultModuleDescriptorCache` to store the hash of the module descriptor from the `ModuleDescriptorStore` as part of the `ModuleDescriptorCacheEntry`.
4. Split CachedArtifactIndex/CachedArtifact from CachedExternalResourceIndex/CachedExternalResource to handle cached Artifacts. Let ArtifactAtRepositoryCachedExternalResourceIndex implement CachedArtifactIndex.
5. Change `CachedArtifact` to add a `descriptorHash` property.
6. Change `CachingModuleVersionRepository.resolve()` to use the owning module version's descriptorHash as the artifact's descriptorHash when storing.
   The final implementation should avoid loading the module descriptor multiple times. It should be possible to attach the module version descriptorHash,
   when resolving the module metadata, to the `Artifact that will later be passed to `resolve()`.
7. Change `CachePolicy.mustRefreshArtifact()` to expire a cached artifact if its descriptorHash != its owning module version's descriptorHash, except
   when offline.
8. Change `CachingModuleVersionRepository.lookupModuleInCache()` so that it no longer calls `expireArtifactsForChangingModule()`.
9. Change `BuildableModuleVersionDescriptor.resolved()` so that an optional "module source" can be provided. Add a corresponding property. All callers
   will use `null` for now.
10. Change `CacheModuleVersionRepository` to use a "module source" that contains the information that it will later need to resolve the artifacts of
    the module - the module descriptor and whether the module is changing or not.
11. Change `ModuleVersionRepository.resolve()` to accept an additional "module source" parameter. Change all callers to use `null`. This will mean that
    `ModuleVersionRepository` can no longer extend `ArtifactResolver` and that `UserResolverChain` will need to create an adapter.
12. Change `UserResolverChain` to call `resolve()` with the "module source" specified in the resolve result.
13. Change `CachingModuleVersionRepository.resolve()` to use the "module source" instead of loading the module descriptor.

### User-visible changes and backward-compatibility issues

* Beside the bugfixes for changing module caching, no user-visible change to cache behaviour.
* New cached module format means updated cache version, requiring artifacts to be re-downloaded where no SHA1 keys are published.
* Since org.apache.ivy.ModuleDescriptor is not part of our public API (except via ivy DependencyResolver), any change to using this internally should not impact users.

### Integration test coverage

* Will download changed version of ${name}-${classifier}.${ext} of changing module referenced with classifier.
* Will download changed version of source jar of changing module referenced with type="source"
    * Verify that we will not download unchanged version of changing module
* Verify that we recover from failed resolution of module after initial successful resolution
    * Failure cases are authorization error (401), server error (500) and connection exception
    * Will re-attempt download on subsequent resolve and recover
    * Will use previously cached version if run with --offline after failure

# Download and parse `maven-metadata.xml` at most once when resolving Maven snapshot versions

* GRADLE-2585.

### Description

Currently, the `maven-metadata.xml` for a version is downloaded and parsed:

* Once when checking for a `pom.xml`
* Once when checking for a JAR, in the case where the POM is missing.
* Once for each artifact download.

This a performance issue, in particular for IDE import, when a snapshot version is resolved multiple times, and multiple artifacts are downloaded. This
is also a correctness issue, as `maven-metadata.xml` may be changed while resolving, meaning that mismatched POM and artifacts may be used.

The goal is to parse the `maven-metadata.xml` exactly once per resolve, mapping the snapshot version to a timestamp version, then use this timestamp
version internally to refer to the resolved version.

### Implementation approach

1. Change `MavenResolver` to override `getDependency()`.
2. In `MavenResolver.getDependency()`, if the requested version is a snapshot:
    1. Attempt to map the requested version to a timestamp version:
        1. Attempt to download `maven-metadata.xml`.
        2. If present, clone the dependency descriptor to add a `timestamp` extra property to the descriptor's `dependencyRevisionId`.
3. Call `super.getDependency()` and return the result.
4. Change `M2ResourcePattern.toPath()` to check for the `timestamp` extra property when building the artifact path, and substitute it into the pattern
   if present.
5. Change `MavenResolver.findIvyFileRef()` and`getArtifactRef()` so that they no longer does any special behaviour for snapshot versions.

In theory, the `timestamp` property added in `getDependency()` will travel back to `getArtifactRef()` at this point.

Some additional refactoring will be done to continue to move away from the Ivy contracts and domain objects:

6. Increment the cache layout version in `DefaultCacheLockingManager`.
7. Change `CachingModuleVersionRepository` so that when resolving the module meta-data, it takes the "module source" returned by
   its delegate repository and stores it in the cached module descriptor entry.
8. Change `CachingModuleVersionRepository` so that when resolving an artifact, it passes the "module source" to its delegate repository.
9. Change `ExternalResourceResolver` to add `getDependency()` and `resolve()` methods that match those of `ModuleVersionRepository`. Change
   `ExternalResourceResolverAdapter` to simply call these methods.
10. Change `MavenResolver.getDependency()` to return a "module source" that includes the timestamp, when known, and to use this when resolving the
    aritfacts.

### User-visible changes and backward-compatibility issues

None, except faster builds for those using snapshots.

### Integration test coverage

* Mostly removing a bunch of `expectMetaDataGet()` calls from various integration tests.
* Check `maven-metadata.xml` is downloaded no more than once per build.

# File stores recover from process crash while adding file to store

When Gradle crashes after writing to a `FileStore` implementation, it can leave a partially written file behind. Subsequent invocations of Gradle
will attempt to use this partial file.

See [GRADLE-2457](http://issues.gradle.org/browse/GRADLE-2457)

### Test coverage

No specific coverage at this point, other than unit testing. At some point we'll set up a stress test.

### Implementation strategy

Something like this:

* Add `IndexableFileStore<K>` interface with a single `FileStoreEntry get(K key)` method.
* Change PathKeyFileStore to implement this method.
* Add `FileStore.add(K key, Action<File> addAction)` method. The action is given a file that it should write the contents to. This initial implementation would
  basically do:
    1. Allocate a temp file using `getTempfile()`
    2. Call `Action.execute(tempfile)` to create the file.
    3. If the action is successful, call `move(key, tempfile)` to move the temp file into place.
* Change `ModuleDescriptorStore` and/or `ModuleDescriptorFileStore` to use a `PathKeyFileStore` to manage access to the actual file store.
* Change `DownloadingRepositoryCacheManager` to use `FileStore.add()` instead of `FileStore.getTempfile()` and `move()`.
* Remove `FileStore.getTempfile()`.
* Change the implementation of `PathKeyFileStore.copy()`, `move()` and `add()` so that it:
    1. Places a marker file down next to the destination file.
    2. Calls `Action.execute(destfile)`
    3. If successful, removes the marker file.
    4. If fails, removes the marker file and the destination file.
    5. Maybe also add some handling to `File.move()` the original destination file out of the way in step 1, and back in on failure in step 4.
* Change `PathKeyFileStore.get()` and `search()` to ignore and/or remove destination files for which a marker file exists.

# Ignore cached missing module entry when module is missing for all repositories

See [GRADLE-2455](http://issues.gradle.org/browse/GRADLE-2455)

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

# Invalid checksum files generated on publish

SHA-1 checksums should be 40 hex characters long. When publishing, Gradle generates a checksum string that does not include leading zeros, so
that sometimes the checksum is shorter than 40 characters.

See [GRADLE-2456](http://issues.gradle.org/browse/GRADLE-2456)

### Test coverage

* Publish an artifact containing the following bytes: [0, 0, 0, 5]. This has an SHA-1 that is 38 hex characters long.
* Assert that the published SHA-1 file contains exactly the following 40 characters: 00e14c6ef59816760e2c9b5a57157e8ac9de4012
* Test the above for Ivy and Maven publication.

### Implementation strategy

* Change `DefaultExternalResourceRepository` to include leading '0's.
* Change `DefaultExternalResourceRepository` to encode the SHA1 file's content using US-ASCII.

# Errors writing cached module descriptor are silently ignored

See [GRADLE-2458](http://issues.gradle.org/browse/GRADLE-2458)

### Test coverage

No specific coverage at this point, other than unit testing.

### Implementation strategy

* Copy XmlModuleDescriptorWriter, add some unit tests.
* Fix XmlModuleDescriptorWriter so that it does not ignore errors.
* Change ModuleDescriptorStore and IvyBackedArtifactPublisher to use this to write the descriptors.

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

# Project dependencies in generated poms use correct artifactIds

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

# Dynamic versions work with authenticated repositories

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
