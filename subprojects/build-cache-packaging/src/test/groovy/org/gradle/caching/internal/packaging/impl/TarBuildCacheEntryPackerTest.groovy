/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching.internal.packaging.impl

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.caching.internal.CacheableEntity
import org.gradle.caching.internal.TestCacheableTree
import org.gradle.caching.internal.origin.OriginReader
import org.gradle.caching.internal.origin.OriginWriter
import org.gradle.internal.file.TreeType
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.FingerprintingStrategy
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint
import org.gradle.internal.hash.DefaultStreamHasher
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.nativeplatform.filesystem.FileSystem
import org.gradle.internal.snapshot.WellKnownFileLocations
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.Specification
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.internal.file.TreeType.DIRECTORY
import static org.gradle.internal.file.TreeType.FILE

@CleanupTestDirectory
class TarBuildCacheEntryPackerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def readOrigin = Stub(OriginReader)
    def writeOrigin = Stub(OriginWriter)

    def fileSystem = Mock(FileSystem)
    def streamHasher = new DefaultStreamHasher()
    def stringInterner = new StringInterner()
    def packer = new TarBuildCacheEntryPacker(fileSystem, streamHasher, stringInterner)
    def fileSystemMirror = new DefaultFileSystemMirror(Stub(WellKnownFileLocations))
    def snapshotter = new DefaultFileSystemSnapshotter(new TestFileHasher(), stringInterner, TestFiles.fileSystem(), fileSystemMirror)

    @Unroll
    def "can pack single file with file mode #mode"() {
        def sourceOutputFile = Spy(File, constructorArgs: [temporaryFolder.file("source.txt").absolutePath]) as File
        sourceOutputFile << "output"
        def targetOutputFile = Spy(File, constructorArgs: [temporaryFolder.file("target.txt").absolutePath]) as File
        def output = new ByteArrayOutputStream()
        def unixMode = Integer.parseInt(mode, 8)

        when:
        pack output, prop(FILE, sourceOutputFile)

        then:
        1 * fileSystem.getUnixMode(sourceOutputFile) >> unixMode
        _ * sourceOutputFile._
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(FILE, targetOutputFile)

        then:
        targetOutputFile.text == "output"
        1 * fileSystem.chmod(targetOutputFile, unixMode)
        _ * targetOutputFile._
        0 * _

        where:
        mode << [ "0644", "0755" ]
    }

    def "can pack directory"() {
        def sourceOutputDir = temporaryFolder.file("source").createDir()
        def sourceSubDir = sourceOutputDir.file("subdir").createDir()
        def sourceDataFile = sourceSubDir.file("data.txt")
        sourceDataFile << "output"
        def targetOutputDir = temporaryFolder.file("target").createDir()
        def targetSubDir = targetOutputDir.file("subdir")
        def targetDataFile = targetSubDir.file("data.txt")
        def output = new ByteArrayOutputStream()
        when:
        def packResult = pack output, prop(DIRECTORY, sourceOutputDir)

        then:
        1 * fileSystem.getUnixMode(sourceSubDir) >> 0711
        1 * fileSystem.getUnixMode(sourceDataFile) >> 0600
        0 * _
        packResult.entries == 4

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        def result = unpack input, prop(DIRECTORY, targetOutputDir)

        then:
        1 * fileSystem.chmod(targetOutputDir, 0755)
        1 * fileSystem.chmod(targetSubDir, 0711)
        1 * fileSystem.chmod(targetDataFile, 0600)
        0 * _
        and:
        targetDataFile.text == "output"
        result.entries == 4
    }

    @Unroll
    def "can pack tree with missing #type (pre-existing as: #preExistsAs)"() {
        def sourceOutput = temporaryFolder.file("source")
        def targetOutput = temporaryFolder.file("target")
        switch (preExistsAs) {
            case "file":
                targetOutput.createNewFile()
                break
            case "dir":
                targetOutput.createDir()
                break
            case "none":
                break
        }
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop(type, sourceOutput)

        then:
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(type, targetOutput)

        then:
        !targetOutput.exists()
        0 * _

        where:
        type      | preExistsAs
        FILE      | "file"
        FILE      | "dir"
        FILE      | "none"
        DIRECTORY | "file"
        DIRECTORY | "dir"
        DIRECTORY | "none"
    }

    @Unroll
    def "can pack single tree file with #type name"() {
        def sourceOutputFile = temporaryFolder.file("source.txt")
        sourceOutputFile << "output"
        def targetOutputFile = temporaryFolder.file("target.txt")
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop(FILE, sourceOutputFile)

        then:
        1 * fileSystem.getUnixMode(sourceOutputFile) >> 0644
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(FILE, targetOutputFile)

        then:
        targetOutputFile.text == "output"
        1 * fileSystem.chmod(targetOutputFile, 0644)
        0 * _

        where:
        type      | treeName
        "long"    | "tree-" + ("x" * 100)
        "unicode" | "tree-dezső"
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    @Unroll
    def "can pack tree directory with files having #type characters in name"() {
        def sourceOutputDir = temporaryFolder.file("source").createDir()
        def sourceOutputFile = sourceOutputDir.file(fileName) << "output"
        def targetOutputDir = temporaryFolder.file("target")
        def targetOutputFile = targetOutputDir.file(fileName)
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop(DIRECTORY, sourceOutputDir)

        then:
        1 * fileSystem.getUnixMode(sourceOutputFile) >> 0644
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(DIRECTORY, targetOutputDir)

        then:
        targetOutputFile.text == "output"
        1 * fileSystem.chmod(targetOutputDir, 0755)
        1 * fileSystem.chmod(targetOutputFile, 0644)
        0 * _

        where:
        type          | fileName
        "ascii-only"  | "input-file.txt"
        "chinese"     | "输入文件.txt"
        "hungarian"   | "Dezső.txt"
        "space"       | "input file.txt"
        "zwnj"        | "input\u200cfile.txt"
        "url-quoted"  | "input%<file>#2.txt"
    }

    @Unroll
    def "can pack trees having #type characters in name"() {
        def sourceOutputDir = temporaryFolder.file("source").createDir()
        def sourceOutputFile = sourceOutputDir.file("output.txt") << "output"
        def targetOutputDir = temporaryFolder.file("target")
        def targetOutputFile = targetOutputDir.file("output.txt")
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop(treeName, DIRECTORY, sourceOutputDir)

        then:
        1 * fileSystem.getUnixMode(sourceOutputFile) >> 0644
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(treeName, DIRECTORY, targetOutputDir)

        then:
        targetOutputFile.text == "output"
        1 * fileSystem.chmod(targetOutputDir, 0755)
        1 * fileSystem.chmod(targetOutputFile, 0644)
        0 * _

        where:
        type          | treeName
        "ascii-only"  | "input-file"
        "chinese"     | "输入文件"
        "hungarian"   | "Dezső"
        "space"       | "input file"
        "zwnj"        | "input\u200cfile"
        "url-quoted"  | "input%<file>#2"
        "file-system" | ":input\\/file:"
    }

    def "can pack entity with all optional, null trees"() {
        def output = new ByteArrayOutputStream()
        when:
        pack output,
            prop("out1", FILE, null),
            prop("out2", DIRECTORY, null)

        then:
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input,
            prop("out1", FILE, null),
            prop("out2", DIRECTORY, null)

        then:
        0 * _
    }

    def "can pack entity with missing files"() {
        def sourceDir = temporaryFolder.file("source")
        def missingSourceFile = sourceDir.file("missing.txt")
        def missingSourceDir = sourceDir.file("missing")
        def targetDir = temporaryFolder.file("target")
        def missingTargetFile = targetDir.file("missing.txt")
        def missingTargetDir = targetDir.file("missing")
        def output = new ByteArrayOutputStream()

        when:
        pack output,
            prop("missingFile", FILE, missingSourceFile),
            prop("missingDir", DIRECTORY, missingSourceDir)

        then:
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input,
            prop("missingFile", FILE, missingTargetFile),
            prop("missingDir", DIRECTORY, missingTargetDir)

        then:
        0 * _
    }

    def "can pack entity with empty directory"() {
        def sourceDir = temporaryFolder.file("source").createDir()
        def targetDir = temporaryFolder.file("target")
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop("empty", DIRECTORY, sourceDir)

        then:
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop("empty", DIRECTORY, targetDir)

        then:
        targetDir.assertIsEmptyDir()
        1 * fileSystem.chmod(targetDir, 0755)
        0 * _
    }

    def pack(OutputStream output, OriginWriter writeOrigin = this.writeOrigin, TreeDefinition... treeDefs) {
        Map<String, CurrentFileCollectionFingerprint> fingerprints = treeDefs.collectEntries { treeDef ->
            return [(treeDef.tree.name): treeDef.fingerprint()]
        }
        packer.pack(entity(treeDefs), fingerprints, output, writeOrigin)
    }

    def unpack(InputStream input, OriginReader readOrigin = this.readOrigin, TreeDefinition... treeDefs) {
        packer.unpack(entity(treeDefs), input, readOrigin)
    }

    def entity(TreeDefinition... treeDefs) {
        Stub(CacheableEntity) {
            visitOutputTrees(_) >> { CacheableEntity.CacheableTreeVisitor visitor ->
                treeDefs.each { visitor.visitOutputTree(it.tree.name, it.tree.type, it.tree.root) }
            }
        }
    }

    def prop(String name = "test", TreeType type, File output, FingerprintingStrategy fingerprintingStrategy = AbsolutePathFingerprintingStrategy.IGNORE_MISSING) {
        switch (type) {
            case FILE:
                return new TreeDefinition(new TestCacheableTree(name, FILE, output)) {
                    @Override
                    CurrentFileCollectionFingerprint fingerprint() {
                        if (output == null) {
                            return fingerprintingStrategy.getEmptyFingerprint()
                        }
                        return DefaultCurrentFileCollectionFingerprint.from([snapshotter.snapshot(output)], fingerprintingStrategy)
                    }
                }
            case DIRECTORY:
                return new TreeDefinition(new TestCacheableTree(name, DIRECTORY, output)) {
                    @Override
                    CurrentFileCollectionFingerprint fingerprint() {
                        if (output == null) {
                            return fingerprintingStrategy.getEmptyFingerprint()
                        }
                        return DefaultCurrentFileCollectionFingerprint.from([snapshotter.snapshot(output)], fingerprintingStrategy)
                    }
                }
            default:
                throw new AssertionError()
        }
    }

    private abstract static class TreeDefinition {
        final TestCacheableTree tree

        TreeDefinition(TestCacheableTree tree) {
            this.tree = tree
        }

        abstract CurrentFileCollectionFingerprint fingerprint()
    }
}
