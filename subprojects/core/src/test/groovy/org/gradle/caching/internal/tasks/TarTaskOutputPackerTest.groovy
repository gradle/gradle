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

import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter
import org.gradle.internal.nativeplatform.filesystem.FileSystem
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.DIRECTORY
import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.FILE

@CleanupTestDirectory
class TarTaskOutputPackerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    def readOrigin = Stub(TaskOutputOriginReader)
    def writeOrigin = Stub(TaskOutputOriginWriter)

    def fileSystem = Mock(FileSystem)
    def packer = new TarTaskOutputPacker(fileSystem)

    @Unroll
    def "can pack single task output file with file mode #mode"() {
        def sourceOutputFile = Spy(File, constructorArgs: [temporaryFolder.file("source.txt").absolutePath])
        sourceOutputFile << "output"
        def targetOutputFile = Spy(File, constructorArgs: [temporaryFolder.file("target.txt").absolutePath])
        def output = new ByteArrayOutputStream()
        def unixMode = Integer.parseInt(mode, 8)

        when:
        pack output,
            new ResolvedTaskOutputFilePropertySpec("test", FILE, sourceOutputFile)

        then:
        1 * fileSystem.getUnixMode(sourceOutputFile) >> unixMode
        _ * sourceOutputFile._
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input,
            new ResolvedTaskOutputFilePropertySpec("test", FILE, targetOutputFile)

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
        pack output, new ResolvedTaskOutputFilePropertySpec("test", DIRECTORY, sourceOutputDir)

        then:
        1 * fileSystem.getUnixMode(sourceSubDir) >> 0711
        1 * fileSystem.getUnixMode(sourceDataFile) >> 0600
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, new ResolvedTaskOutputFilePropertySpec("test", DIRECTORY, targetOutputDir)

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
        pack output, new ResolvedTaskOutputFilePropertySpec("test", type, sourceOutput)

        then:
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, new ResolvedTaskOutputFilePropertySpec("test", type, targetOutput)

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

    def "can pack single task output file with long name"() {
        def propertyName = "prop-" + ("x" * 100)
        def sourceOutputFile = temporaryFolder.file("source.txt")
        sourceOutputFile << "output"
        def targetOutputFile = temporaryFolder.file("target.txt")
        def output = new ByteArrayOutputStream()
        when:
        pack output, new ResolvedTaskOutputFilePropertySpec(propertyName, FILE, sourceOutputFile)

        then:
        noExceptionThrown()
        1 * fileSystem.getUnixMode(sourceOutputFile) >> 0644
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, new ResolvedTaskOutputFilePropertySpec(propertyName, FILE, targetOutputFile)

        then:
        1 * fileSystem.chmod(targetOutputFile, 0644)
        then:
        targetOutputFile.text == "output"
        0 * _
    }

    def "can pack task output with all optional, null outputs"() {
        def output = new ByteArrayOutputStream()
        when:
        pack output,
            new ResolvedTaskOutputFilePropertySpec("out1", FILE, null),
            new ResolvedTaskOutputFilePropertySpec("out2", DIRECTORY, null)

        then:
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input,
            new ResolvedTaskOutputFilePropertySpec("out1", FILE, null),
            new ResolvedTaskOutputFilePropertySpec("out2", DIRECTORY, null)

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
            new ResolvedTaskOutputFilePropertySpec("missingFile", FILE, missingSourceFile),
            new ResolvedTaskOutputFilePropertySpec("missingDir", DIRECTORY, missingSourceDir)

        then:
        noExceptionThrown()
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input,
            new ResolvedTaskOutputFilePropertySpec("missingFile", FILE, missingTargetFile),
            new ResolvedTaskOutputFilePropertySpec("missingDir", DIRECTORY, missingTargetDir)

        then:
        noExceptionThrown()
        0 * _
    }

    def "can pack task output with empty output directory"() {
        def sourceDir = temporaryFolder.file("source").createDir()
        def targetDir = temporaryFolder.file("target")
        def output = new ByteArrayOutputStream()
        when:
        pack output, new ResolvedTaskOutputFilePropertySpec("empty", DIRECTORY, sourceDir)

        then:
        noExceptionThrown()
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        unpack input, new ResolvedTaskOutputFilePropertySpec("empty", DIRECTORY, targetDir)

        then:
        noExceptionThrown()
        1 * fileSystem.chmod(targetDir, 0755)
        then:
        targetDir.assertIsEmptyDir()
        0 * _
    }

    def pack(OutputStream output, TaskOutputOriginWriter writeOrigin = this.writeOrigin, ResolvedTaskOutputFilePropertySpec... propertySpecs) {
        pack(output, writeOrigin, propertySpecs as SortedSet)
    }

    def pack(OutputStream output, TaskOutputOriginWriter writeOrigin = this.writeOrigin, SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs) {
        packer.pack(propertySpecs, output, writeOrigin)
    }

    def unpack(InputStream input, TaskOutputOriginReader readOrigin = this.readOrigin, ResolvedTaskOutputFilePropertySpec... propertySpecs) {
        unpack(input, readOrigin, propertySpecs as SortedSet)
    }

    def unpack(InputStream input, TaskOutputOriginReader readOrigin = this.readOrigin, SortedSet<ResolvedTaskOutputFilePropertySpec> propertySpecs) {
        packer.unpack(propertySpecs, input, readOrigin)
    }
}
