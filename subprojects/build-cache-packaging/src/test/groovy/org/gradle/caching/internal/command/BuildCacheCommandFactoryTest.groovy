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

package org.gradle.caching.internal.command

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.CacheableEntity
import org.gradle.caching.internal.TestCacheableTree
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.caching.internal.origin.OriginMetadataFactory
import org.gradle.caching.internal.origin.OriginReader
import org.gradle.caching.internal.origin.OriginWriter
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker
import org.gradle.caching.internal.packaging.CacheableTree
import org.gradle.caching.internal.packaging.UnrecoverableUnpackingException
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.nativeintegration.filesystem.DefaultFileMetadata
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemMirror
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.time.Timer
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.Specification
import org.junit.Rule

import static org.gradle.caching.internal.packaging.CacheableTree.Type.DIRECTORY
import static org.gradle.caching.internal.packaging.CacheableTree.Type.FILE

@CleanupTestDirectory
class BuildCacheCommandFactoryTest extends Specification {
    def packer = Mock(BuildCacheEntryPacker)
    def originFactory = Mock(OriginMetadataFactory)
    def fileSystemMirror = Mock(FileSystemMirror)
    def stringInterner = new StringInterner()
    def commandFactory = new BuildCacheCommandFactory(packer, originFactory, fileSystemMirror, stringInterner)

    def key = Mock(BuildCacheKey)
    def entry = Mock(CacheableEntity)
    def loadListener = Mock(BuildCacheLoadListener)
    def timer = Stub(Timer)

    def originMetadata = Mock(OriginMetadata)
    def originReader = Mock(OriginReader)
    def originWriter = Mock(OriginWriter)

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def localStateFile = temporaryFolder.file("local-state.txt").createFile()
    def localStateFiles = ImmutableFileCollection.of(localStateFile)

    def "load invokes unpacker and fingerprints trees"() {
        def outputFile = temporaryFolder.file("output.txt")
        def outputDir = temporaryFolder.file("outputDir")
        def outputDirFile = outputDir.file("file.txt")
        def input = Mock(InputStream)
        def trees = [
            prop("outputDir", DIRECTORY, outputDir),
            prop("outputFile", FILE, outputFile),
        ] as SortedSet
        def load = commandFactory.createLoad(key, trees, entry, localStateFiles, loadListener)

        def outputFileSnapshot = new RegularFileSnapshot(outputFile.absolutePath, outputFile.name, HashCode.fromInt(234), 234)
        def fileSnapshots = ImmutableMap.of(
            "outputDir", new DirectorySnapshot(outputDir.getAbsolutePath(), outputDir.name, ImmutableList.of(new RegularFileSnapshot(outputDirFile.getAbsolutePath(), outputDirFile.name, HashCode.fromInt(123), 123)), HashCode.fromInt(456)),
            "outputFile", outputFileSnapshot)

        when:
        def result = load.load(input)

        then:
        1 * loadListener.beforeLoad()
        1 * originFactory.createReader(entry) >> originReader

        then:
        1 * packer.unpack(trees, input, originReader) >> new BuildCacheEntryPacker.UnpackResult(originMetadata, 123L, fileSnapshots)

        then:
        1 * fileSystemMirror.putMetadata(outputDir.absolutePath, DefaultFileMetadata.directory())
        1 * fileSystemMirror.putSnapshot(_ as DirectorySnapshot) >> { args ->
            DirectorySnapshot snapshot = args[0]
            assert snapshot.absolutePath == outputDir.absolutePath
            assert snapshot.name == outputDir.name
        }
        1 * fileSystemMirror.putSnapshot(_ as RegularFileSnapshot) >> { args ->
            RegularFileSnapshot snapshot = args[0]
            assert snapshot.absolutePath == outputFileSnapshot.absolutePath
            assert snapshot.name == outputFileSnapshot.name
            assert snapshot.hash == outputFileSnapshot.hash
        }
        1 * loadListener.afterLoad(_ as ImmutableSortedMap<String, CurrentFileCollectionFingerprint>, originMetadata as OriginMetadata) >> { ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprints, OriginMetadata metadata ->
            assert fingerprints.keySet() as List == ["outputDir", "outputFile"]
            assert fingerprints["outputFile"].fingerprints.keySet() == [outputFile.absolutePath] as Set
            assert fingerprints["outputDir"].fingerprints.keySet() == [outputDir, outputDirFile]*.absolutePath as Set
        }

        then:
        result.artifactEntryCount == 123
        result.metadata == originMetadata
        0 * _

        then:
        !localStateFile.exists()
    }

    def "after failed unpacking output is cleaned up"() {
        def input = Mock(InputStream)
        def outputFile = temporaryFolder.file("output.txt")
        def trees = props("output", FILE, outputFile)
        def command = commandFactory.createLoad(key, trees, entry, localStateFiles, loadListener)

        when:
        command.load(input)

        then:
        1 * loadListener.beforeLoad()
        1 * originFactory.createReader(entry) >> originReader

        then:
        1 * packer.unpack(trees, input, originReader) >> {
            outputFile << "partially extracted output fil..."
            throw new RuntimeException("unpacking error")
        }

        then:
        1 * loadListener.afterLoad(_ as Throwable)

        then:
        def ex = thrown Exception
        !(ex instanceof UnrecoverableUnpackingException)
        ex.cause.message == "unpacking error"
        !outputFile.exists()
        0 * _

        then:
        !localStateFile.exists()
    }

    def "error during cleanup of failed unpacking is reported"() {
        def input = Mock(InputStream)
        def trees = Mock(SortedSet)
        def command = commandFactory.createLoad(key, trees, entry, localStateFiles, loadListener)

        when:
        command.load(input)

        then:
        1 * loadListener.beforeLoad()
        1 * originFactory.createReader(entry) >> originReader

        then:
        1 * packer.unpack(trees, input, originReader) >> {
            throw new RuntimeException("unpacking error")
        }

        then:
        1 * trees.iterator() >> { throw new RuntimeException("cleanup error") }

        then:
        def ex = thrown UnrecoverableUnpackingException
        ex.cause.message == "unpacking error"
        0 * _

        then:
        !localStateFile.exists()
    }

    def "store invokes packer"() {
        def output = Mock(OutputStream)
        def trees = props("output")
        def outputFingerprints = Mock(Map)
        def command = commandFactory.createStore(key, trees, outputFingerprints, entry, 421L)

        when:
        def result = command.store(output)

        then:
        1 * originFactory.createWriter(entry, 421L) >> originWriter

        then:
        1 * packer.pack(trees, outputFingerprints, output, originWriter) >> new BuildCacheEntryPacker.PackResult(123)

        then:
        result.artifactEntryCount == 123
        0 * _
    }

    def props(String name, CacheableTree.Type type = FILE, File root = null) {
        return [prop(name, type, root)] as SortedSet
    }

    CacheableTree prop(String name, CacheableTree.Type type = FILE, File root = null) {
        new TestCacheableTree(name, type, root)
    }
}
