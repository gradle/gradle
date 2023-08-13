/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.caching.internal

import com.google.common.io.ByteStreams
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.controller.CacheManifest
import org.gradle.caching.internal.controller.NextGenBuildCacheController
import org.gradle.caching.internal.operations.BuildCacheArchivePackBuildOperationType
import org.gradle.caching.internal.operations.BuildCacheArchiveUnpackBuildOperationType
import org.gradle.caching.internal.operations.BuildCacheRemoteLoadBuildOperationType
import org.gradle.caching.internal.operations.BuildCacheRemoteStoreBuildOperationType
import org.gradle.caching.local.internal.DefaultBuildCacheTempFileStore
import org.gradle.caching.local.internal.H2BuildCacheService
import org.gradle.caching.local.internal.LocalBuildCacheService
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.TestBuildCache
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.time.Time
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.internal.TextUtil
import spock.lang.Shared

import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

@LeaksFileHandles("https://github.com/gradle/gradle-private/issues/3916")
class NextGenBuildCacheBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    @Shared
    String localCacheClass = "LocalBuildCache"
    @Shared
    String remoteCacheClass = "RemoteBuildCache"

    def operations = new BuildOperationsFixture(executer, testDirectoryProvider)

    void local(String loadBody, String storeBody) {
        register(localCacheClass, loadBody, storeBody, true)
    }

    // TODO Add push parameter
    void remote(String loadBody, String storeBody) {
        register(remoteCacheClass, loadBody, storeBody)
    }

    def setup() {
        executer.beforeExecute { withBuildCacheNgEnabled() }
    }

    void register(String className, String loadBody, String storeBody, boolean isLocal = false) {
        settingsFile << """
            class ${className} extends AbstractBuildCache {}
            class ${className}ServiceFactory implements BuildCacheServiceFactory<${className}> {
                ${className}Service createBuildCacheService(${className} configuration, Describer describer) {
                    return new ${className}Service(configuration)
                }
            }
            class ${className}Service implements BuildCacheService ${isLocal ? ", ${LocalBuildCacheService.name}" : ""} {
                ${className}Service(${className} configuration) {
                }

                @Override
                boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                    ${isLocal ? "" : loadBody ?: ""}
                }

                @Override
                void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                    ${isLocal ? "" : storeBody ?: ""}
                }

                // @Override
                void loadLocally(BuildCacheKey key, Action<? super File> reader) {
                    ${isLocal ? loadBody ?: "" : ""}
                }

                // @Override
                void storeLocally(BuildCacheKey key, File file) {
                    ${isLocal ? storeBody ?: "" : ""}
                }

                void withTempFile(BuildCacheKey key, Action<? super File> action) {
                    new $DefaultBuildCacheTempFileStore.name(new File("${TextUtil.normaliseFileSeparators(file("tmp").absolutePath)}")).withTempFile(key, action)
                }

                @Override
                void close() throws IOException {
                }
            }

            buildCache {
                registerBuildCacheService(${className}, ${className}ServiceFactory)
            }
        """
    }

    String cacheableTask() {
        """
            @CacheableTask
            class CustomTask extends DefaultTask {

                @Input
                String val = "foo"

                @Input
                List<String> paths = []

                @OutputDirectory
                File dir = project.file("build/dir")

                @OutputDirectory
                File otherDir = project.file("build/otherDir")

                @TaskAction
                void generate() {
                    paths.each {
                        def f = new File(dir, it)
                        f.parentFile.mkdirs()
                        f.text = val
                    }
                }
            }
        """
    }

    def "emits only pack/unpack operations for local"() {
        def localCache = new TestBuildCache(file("local-cache"))
        settingsFile << localCache.localCacheConfiguration()

        when:
        buildFile << cacheableTask() << """
            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """

        succeeds("t")

        then:
        operations.none(BuildCacheRemoteLoadBuildOperationType)
        operations.none(BuildCacheRemoteStoreBuildOperationType)
        def packOp = operations.only(BuildCacheArchivePackBuildOperationType)

        packOp.details.cacheKey != null
        // packOp.result.archiveSize == localCache.cacheArtifact(packOp.details.cacheKey.toString()).length()
        // We store the manifest + the content once (even though it's present in two output files)
        packOp.result.archiveEntryCount == 2

        when:
        succeeds("clean", "t")

        then:
        operations.none(BuildCacheRemoteStoreBuildOperationType)
        def unpackOp = operations.only(BuildCacheArchiveUnpackBuildOperationType)
        unpackOp.details.cacheKey == packOp.details.cacheKey
        unpackOp.details.archiveSize > 0
        // We load the manifest + the content once (even though it's present in two output files)
        unpackOp.result.archiveEntryCount == 2
    }

    def "records load failure for #exceptionType"() {
        def localCache = new TestBuildCache(file("local-cache"))
        settingsFile << localCache.localCacheConfiguration()

        when:
        remote("throw new ${exceptionType.name}('!')", "writer.writeTo(new ${NullOutputStream.name}())")
        settingsFile << """
            buildCache { remote($remoteCacheClass) }
        """
        buildFile << cacheableTask() << """
            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """

        executer.withStackTraceChecksDisabled()
        succeeds("t")

        then:
        def failedLoadOp = operations.only(BuildCacheRemoteLoadBuildOperationType)
        failedLoadOp.details.cacheKey != null
        failedLoadOp.result == null
        failedLoadOp.failure == "${exceptionType.name}: !"

        where:
        exceptionType << [RuntimeException, IOException]
    }

    def "records store failure for #exceptionType"() {
        def localCache = new TestBuildCache(file("local-cache"))
        settingsFile << localCache.localCacheConfiguration()

        when:
        remote("", "throw new ${exceptionType.name}('!')")
        settingsFile << """
            buildCache {
                remote($remoteCacheClass).push = true
            }
        """
        buildFile << cacheableTask() << """
            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """

        executer.withStackTraceChecksDisabled()
        succeeds("t")

        then:
        def failedLoadOp = operations.only(BuildCacheRemoteStoreBuildOperationType)
        failedLoadOp.details.cacheKey != null
        failedLoadOp.result == null
        failedLoadOp.failure == "${exceptionType.name}: !"

        where:
        exceptionType << [RuntimeException, BuildCacheException, IOException]
    }

    def "records #target unpack failure"() {
        def localCache = new TestBuildCache(file("local-cache"))
        settingsFile << localCache.localCacheConfiguration()

        buildFile << cacheableTask() << """
            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """

        run("t")

        def packOp = operations.only(BuildCacheArchivePackBuildOperationType)
        def manifestKey = new DefaultBuildCacheKey(HashCode.fromString(packOp.details.cacheKey as String))

        // Corrupt cached artifact
        try (def service = new H2BuildCacheService(localCache.cacheDir.toPath(), 10, 7, Time.clock())) {
            service.open()

            BuildCacheKey cacheKeyToCorrupt = null
            if (target == "manifest") {
                cacheKeyToCorrupt = manifestKey
            } else if (target == "content") {
                service.load(manifestKey, input -> {
                    def manifestBytes = ByteStreams.toByteArray(new GZIPInputStream(input));
                    def manifestText = new String(manifestBytes, StandardCharsets.UTF_8)
                    def manifest = NextGenBuildCacheController.createGson().fromJson(manifestText, CacheManifest.class)
                    cacheKeyToCorrupt = new DefaultBuildCacheKey(manifest.propertyManifests.values().stream()
                        .flatMap(List::stream)
                        .filter(entry -> entry.type == FileType.RegularFile)
                        .map(CacheManifest.ManifestEntry::getContentHash)
                        .findFirst()
                        .get())
                });
            }
            assert service.remove(cacheKeyToCorrupt)
            service.store(cacheKeyToCorrupt, new BuildCacheEntryWriter() {
                @Override
                void writeTo(OutputStream output) throws IOException {
                    output.write([1, 2, 3, 4] as byte[])
                }

                @Override
                long getSize() {
                    return 4
                }
            })
        }

        when:
        fails("clean", "t")

        then:
        def failedUnpackOp = operations.only(BuildCacheArchiveUnpackBuildOperationType)
        failedUnpackOp.details.cacheKey == manifestKey.toString()
        failedUnpackOp.result == null
        failedUnpackOp.failure =~ /java.util.zip.ZipException: Not in GZIP format/

        where:
        target << [
            "manifest",
            "content"
        ]
    }

    def "records ops for miss then store"() {
        def localCache = new TestBuildCache(file("local-cache"))
        settingsFile << localCache.localCacheConfiguration()

        given:
        remote("", "writer.writeTo(new ${NullOutputStream.name}())")

        settingsFile << """
            buildCache {
                remote($remoteCacheClass) { push = true }
            }
        """

        buildFile << cacheableTask() << """
            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """

        when:
        succeeds("t")

        then:
        def remoteMissLoadOp = operations.only(BuildCacheRemoteLoadBuildOperationType)
        def packOp = operations.only(BuildCacheArchivePackBuildOperationType)
        // Store the manifest plus teh content

        def remoteStoreOp = operations.only(BuildCacheRemoteStoreBuildOperationType)

        packOp.details.cacheKey == remoteStoreOp.details.cacheKey
        packOp.result.archiveSize > 0
        packOp.result.archiveEntryCount == 2
        remoteStoreOp.details.archiveSize > 0

        operations.orderedSerialSiblings(remoteMissLoadOp, packOp)
        operations.parentsOf(remoteStoreOp).contains(packOp)
    }

    def "records ops for remote hit"() {
        given:
        buildFile << cacheableTask() << """
            apply plugin: "base"
            tasks.create("t", CustomTask).paths << "out1" << "out2"
        """
        def buildCache = new TestBuildCache(testDirectory.file("build-cache-dir").createDir())

        def artifactFile = file("artifact")
        remote("", "new File('${TextUtil.normaliseFileSeparators(artifactFile.absolutePath)}').withOutputStream { writer.writeTo(it) }")
        settingsFile << """
            ${buildCache.localCacheConfiguration()}
            buildCache {
                remote($remoteCacheClass) {
                    push = true
                }
            }
        """

        succeeds("t")
        def initialPackOp = operations.only(BuildCacheArchivePackBuildOperationType)
        def initialStoreOp = operations.only(BuildCacheRemoteStoreBuildOperationType)
        // Clean local cache
        buildCache.cacheDir.deleteDir()

        when:
        settingsFile.text = ""
        remote("new File('${TextUtil.normaliseFileSeparators(artifactFile.absolutePath)}').withInputStream { reader.readFrom(it) }", "writer.writeTo(new ${NullOutputStream.name}())")
        settingsFile << """
            ${buildCache.localCacheConfiguration()}
            buildCache {
                remote($remoteCacheClass) {
                    push = true
                }
            }
        """

        succeeds("clean", "t")

        then:
        def remoteHitLoadOp = operations.only(BuildCacheRemoteLoadBuildOperationType)
        def unpackOp = operations.only(BuildCacheArchiveUnpackBuildOperationType)

        remoteHitLoadOp.details.cacheKey == initialPackOp.details.cacheKey
        unpackOp.details.cacheKey == remoteHitLoadOp.details.cacheKey
        unpackOp.result.archiveEntryCount == 2
        unpackOp.details.archiveSize > 0
        remoteHitLoadOp.result.archiveSize > 0

        operations.parentsOf(unpackOp).contains(remoteHitLoadOp)
    }
}
