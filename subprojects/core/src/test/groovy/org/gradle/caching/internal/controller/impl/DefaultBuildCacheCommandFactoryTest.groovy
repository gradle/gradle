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

package org.gradle.caching.internal.controller.impl

import groovy.transform.Immutable
import org.gradle.api.internal.cache.StringInterner
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.CacheableEntity
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.caching.internal.origin.OriginMetadataFactory
import org.gradle.caching.internal.origin.OriginReader
import org.gradle.caching.internal.origin.OriginWriter
import org.gradle.caching.internal.packaging.BuildCacheEntryPacker
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.TreeType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.internal.snapshot.SnapshotVisitorUtil
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.file.TreeType.DIRECTORY
import static org.gradle.internal.file.TreeType.FILE

@CleanupTestDirectory
class DefaultBuildCacheCommandFactoryTest extends Specification {
    def packer = Mock(BuildCacheEntryPacker)
    def originFactory = Mock(OriginMetadataFactory)
    def fileSystemAccess = Mock(FileSystemAccess)
    def stringInterner = new StringInterner()
    def commandFactory = new DefaultBuildCacheCommandFactory(packer, originFactory, fileSystemAccess, stringInterner)

    def key = Mock(BuildCacheKey)

    def originMetadata = Mock(OriginMetadata)
    def originReader = Mock(OriginReader)
    def originWriter = Mock(OriginWriter)

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "load invokes unpacker and fingerprints trees"() {
        def outputFile = temporaryFolder.file("output.txt")
        def outputDir = temporaryFolder.file("outputDir")
        def outputDirFile = outputDir.file("file.txt")
        def input = Mock(InputStream)
        def entity = entity(
            prop("outputDir", DIRECTORY, outputDir),
            prop("outputFile", FILE, outputFile)
        )
        def load = commandFactory.createLoad(key, entity)

        def outputFileSnapshot = new RegularFileSnapshot(outputFile.absolutePath, outputFile.name, HashCode.fromInt(234), DefaultFileMetadata.file(15, 234, AccessType.DIRECT))
        def fileSnapshots = [
            outputDir: new DirectorySnapshot(outputDir.getAbsolutePath(), outputDir.name, AccessType.DIRECT, HashCode.fromInt(456), [
                new RegularFileSnapshot(outputDirFile.getAbsolutePath(), outputDirFile.name, HashCode.fromInt(123), DefaultFileMetadata.file(46, 123, AccessType.DIRECT))
            ]),
            outputFile: outputFileSnapshot
        ]

        when:
        def result = load.load(input)

        then:
        1 * originFactory.createReader(entity) >> originReader
        1 * fileSystemAccess.write([outputDir.absolutePath, outputFile.absolutePath], _)

        then:
        1 * packer.unpack(entity, input, originReader) >> new BuildCacheEntryPacker.UnpackResult(originMetadata, 123L, fileSnapshots)

        then:
        1 * fileSystemAccess.record(_ as DirectorySnapshot) >> { FileSystemLocationSnapshot snapshot  ->
            assert snapshot.absolutePath == outputDir.absolutePath
            assert snapshot.name == outputDir.name
        }
        1 * fileSystemAccess.record(_ as RegularFileSnapshot) >> { FileSystemLocationSnapshot snapshot ->
            assert snapshot.absolutePath == outputFileSnapshot.absolutePath
            assert snapshot.name == outputFileSnapshot.name
            assert snapshot.hash == outputFileSnapshot.hash
        }

        then:
        result.artifactEntryCount == 123
        result.metadata.originMetadata == originMetadata
        result.metadata.resultingSnapshots.keySet() as List == ["outputDir", "outputFile"]
        SnapshotVisitorUtil.getAbsolutePaths(result.metadata.resultingSnapshots["outputFile"], true) == [outputFile.absolutePath]
        SnapshotVisitorUtil.getAbsolutePaths(result.metadata.resultingSnapshots["outputDir"], true) == [outputDir.absolutePath, outputDirFile.absolutePath]
        0 * _
    }

    def "after failed unpacking error is propagated and output is not removed"() {
        def input = Mock(InputStream)
        def outputFile = temporaryFolder.file("output.txt")
        def entity = this.entity(prop("output", FILE, outputFile))
        def command = commandFactory.createLoad(key, entity)

        when:
        command.load(input)

        then:
        1 * originFactory.createReader(entity) >> originReader
        1 * fileSystemAccess.write([outputFile.absolutePath], _)

        then:
        1 * packer.unpack(entity, input, originReader) >> {
            outputFile << "partially extracted output fil..."
            throw new RuntimeException("unpacking error")
        }

        then:
        def ex = thrown Exception
        ex.message == "unpacking error"
        outputFile.exists()
        0 * _
    }

    def "store invokes packer"() {
        def output = Mock(OutputStream)
        def entity = entity(prop("output"))
        def outputSnapshots = Mock(Map)
        def command = commandFactory.createStore(key, entity, outputSnapshots, 421L)

        when:
        def result = command.store(output)

        then:
        1 * originFactory.createWriter(entity, 421L) >> originWriter

        then:
        1 * packer.pack(entity, outputSnapshots, output, originWriter) >> new BuildCacheEntryPacker.PackResult(123)

        then:
        result.artifactEntryCount == 123
        0 * _
    }

    def entity(TestCacheableTree... trees) {
        return Stub(CacheableEntity) {
            visitOutputTrees(_ as CacheableEntity.CacheableTreeVisitor) >> { CacheableEntity.CacheableTreeVisitor visitor ->
                trees.each { visitor.visitOutputTree(it.name, it.type, it.root) }
            }
        }
    }

    TestCacheableTree prop(String name, TreeType type = FILE, File root = null) {
        new TestCacheableTree(name, type, root)
    }

    @Immutable(knownImmutableClasses = [File])
    private static class TestCacheableTree {
        String name
        TreeType type
        File root
    }
}
