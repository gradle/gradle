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

package org.gradle.caching.internal.tasks

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultFileSystemMirror
import org.gradle.api.internal.changedetection.state.DefaultFileSystemSnapshotter
import org.gradle.api.internal.changedetection.state.EmptyFileCollectionSnapshot
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot
import org.gradle.api.internal.changedetection.state.WellKnownFileLocations
import org.gradle.api.internal.changedetection.state.mirror.logical.AbsolutePathFingerprintingStrategy
import org.gradle.api.internal.changedetection.state.mirror.logical.DefaultFileCollectionFingerprint
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.OutputType
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter
import org.gradle.internal.hash.DefaultStreamHasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.hash.TestFileHasher
import org.gradle.internal.nativeplatform.filesystem.FileSystem
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Callable

import static org.gradle.api.internal.tasks.OutputType.DIRECTORY
import static org.gradle.api.internal.tasks.OutputType.FILE

@CleanupTestDirectory
class TarTaskOutputPackerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def readOrigin = Stub(TaskOutputOriginReader)
    def writeOrigin = Stub(TaskOutputOriginWriter)

    def fileSystem = Mock(FileSystem)
    def streamHasher = new DefaultStreamHasher({ Hashing.md5().newHasher() })
    def stringInterner = new StringInterner()
    def packer = new TarTaskOutputPacker(fileSystem, streamHasher, stringInterner)
    def fileSystemMirror = new DefaultFileSystemMirror(Stub(WellKnownFileLocations))
    def snapshotter = new DefaultFileSystemSnapshotter(new TestFileHasher(), stringInterner, TestFiles.fileSystem(), TestFiles.directoryFileTreeFactory(), fileSystemMirror)
    def dirTreeFactory = TestFiles.directoryFileTreeFactory()

    @Unroll
    def "can pack single task output file with file mode #mode"() {
        def sourceOutputFile = Spy(File, constructorArgs: [temporaryFolder.file("source.txt").absolutePath])
        sourceOutputFile << "output"
        def targetOutputFile = Spy(File, constructorArgs: [temporaryFolder.file("target.txt").absolutePath])
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
        1 * fileSystem.chmod(targetOutputFile, unixMode)
        _ * targetOutputFile._
        then:
        targetOutputFile.text == "output"
        0 * _

        where:
        mode << [ "0644", "0755" ]
    }

    def "can pack task output directory"() {
        def sourceOutputDir = temporaryFolder.file("source").createDir()
        def sourceSubDir = sourceOutputDir.file("subdir").createDir()
        def sourceDataFile = sourceSubDir.file("data.txt")
        sourceDataFile << "output"
        def targetOutputDir = temporaryFolder.file("target").createDir()
        def targetSubDir = targetOutputDir.file("subdir")
        def targetDataFile = targetSubDir.file("data.txt")
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop(DIRECTORY, sourceOutputDir)

        then:
        1 * fileSystem.getUnixMode(sourceSubDir) >> 0711
        1 * fileSystem.getUnixMode(sourceDataFile) >> 0600
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(DIRECTORY, targetOutputDir)

        then:
        1 * fileSystem.chmod(targetOutputDir, 0755)
        1 * fileSystem.chmod(targetSubDir, 0711)
        1 * fileSystem.chmod(targetDataFile, 0600)
        then:
        targetDataFile.text == "output"
        0 * _
    }

    @Unroll
    def "can pack task output with missing #type (pre-existing as: #preExistsAs)"() {
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
    def "can pack single task output file with #type name"() {
        def sourceOutputFile = temporaryFolder.file("source.txt")
        sourceOutputFile << "output"
        def targetOutputFile = temporaryFolder.file("target.txt")
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop(FILE, sourceOutputFile)

        then:
        noExceptionThrown()
        1 * fileSystem.getUnixMode(sourceOutputFile) >> 0644
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(FILE, targetOutputFile)

        then:
        1 * fileSystem.chmod(targetOutputFile, 0644)
        then:
        targetOutputFile.text == "output"
        0 * _

        where:
        type      | propertyName
        "long"    | "prop-" + ("x" * 100)
        "unicode" | "prop-dezső"
    }

    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    @Unroll
    def "can pack output directory with files having #type characters in name"() {
        def sourceOutputDir = temporaryFolder.file("source").createDir()
        def sourceOutputFile = sourceOutputDir.file(fileName) << "output"
        def targetOutputDir = temporaryFolder.file("target")
        def targetOutputFile = targetOutputDir.file(fileName)
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop(DIRECTORY, sourceOutputDir)

        then:
        noExceptionThrown()
        1 * fileSystem.getUnixMode(sourceOutputFile) >> 0644
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(DIRECTORY, targetOutputDir)

        then:
        1 * fileSystem.chmod(targetOutputDir, 0755)
        1 * fileSystem.chmod(targetOutputFile, 0644)
        then:
        targetOutputFile.text == "output"
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
    def "can pack output properties having #type characters in name"() {
        def sourceOutputDir = temporaryFolder.file("source").createDir()
        def sourceOutputFile = sourceOutputDir.file("output.txt") << "output"
        def targetOutputDir = temporaryFolder.file("target")
        def targetOutputFile = targetOutputDir.file("output.txt")
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop(propertyName, DIRECTORY, sourceOutputDir)

        then:
        noExceptionThrown()
        1 * fileSystem.getUnixMode(sourceOutputFile) >> 0644
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop(propertyName, DIRECTORY, targetOutputDir)

        then:
        1 * fileSystem.chmod(targetOutputDir, 0755)
        1 * fileSystem.chmod(targetOutputFile, 0644)
        then:
        targetOutputFile.text == "output"
        0 * _

        where:
        type          | propertyName
        "ascii-only"  | "input-file"
        "chinese"     | "输入文件"
        "hungarian"   | "Dezső"
        "space"       | "input file"
        "zwnj"        | "input\u200cfile"
        "url-quoted"  | "input%<file>#2"
        "file-system" | ":input\\/file:"
    }

    def "can pack task output with all optional, null outputs"() {
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
        noExceptionThrown()
        0 * _
    }

    def "can pack task output with missing files"() {
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
        noExceptionThrown()
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input,
            prop("missingFile", FILE, missingTargetFile),
            prop("missingDir", DIRECTORY, missingTargetDir)

        then:
        noExceptionThrown()
        0 * _
    }

    def "can pack task output with empty output directory"() {
        def sourceDir = temporaryFolder.file("source").createDir()
        def targetDir = temporaryFolder.file("target")
        def output = new ByteArrayOutputStream()
        when:
        pack output, prop("empty", DIRECTORY, sourceDir)

        then:
        noExceptionThrown()
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, prop("empty", DIRECTORY, targetDir)

        then:
        noExceptionThrown()
        1 * fileSystem.chmod(targetDir, 0755)
        then:
        targetDir.assertIsEmptyDir()
        0 * _
    }

    def pack(OutputStream output, TaskOutputOriginWriter writeOrigin = this.writeOrigin, PropertyDefinition... propertyDefs) {
        def propertySpecs = propertyDefs*.property as SortedSet
        def outputSnapshots = propertyDefs.collectEntries { propertyDef ->
            return [(propertyDef.property.propertyName): propertyDef.outputSnapshot()]
        }
        packer.pack(propertySpecs, outputSnapshots, output, writeOrigin)
    }

    def unpack(InputStream input, TaskOutputOriginReader readOrigin = this.readOrigin, PropertyDefinition... propertyDefs) {
        def propertySpecs = propertyDefs*.property as SortedSet
        packer.unpack(propertySpecs, input, readOrigin)
    }

    def prop(String name = "test", OutputType type, File output) {
        switch (type) {
            case FILE:
                return new PropertyDefinition(new ResolvedTaskOutputFilePropertySpec(name, FILE, output), {
                    if (output == null) {
                        return EmptyFileCollectionSnapshot.INSTANCE
                    }
                    return DefaultFileCollectionFingerprint.from([snapshotter.snapshotSelf(output)], new AbsolutePathFingerprintingStrategy(false))
                })
            case DIRECTORY:
                return new PropertyDefinition(new ResolvedTaskOutputFilePropertySpec(name, DIRECTORY, output), {
                    if (output == null) {
                        return EmptyFileCollectionSnapshot.INSTANCE
                    }
                    return DefaultFileCollectionFingerprint.from([snapshotter.snapshotDirectoryTree(dirTreeFactory.create(output))], new AbsolutePathFingerprintingStrategy(false))
                })
            default:
                throw new AssertionError()
        }
    }

    private static class PropertyDefinition {
        ResolvedTaskOutputFilePropertySpec property
        Callable<FileCollectionSnapshot> outputSnapshot

        PropertyDefinition(ResolvedTaskOutputFilePropertySpec property, Callable<FileCollectionSnapshot> outputSnapshot) {
            this.property = property
            this.outputSnapshot = outputSnapshot
        }
    }
}
