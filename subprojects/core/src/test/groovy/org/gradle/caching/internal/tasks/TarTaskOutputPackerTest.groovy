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
import org.gradle.internal.nativeplatform.filesystem.FileSystem
import spock.lang.Unroll

import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.DIRECTORY
import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.FILE

class TarTaskOutputPackerTest extends AbstractTaskOutputPackerSpec {
    def fileSystem = Mock(FileSystem)
    private tarPacker = new TarTaskOutputPacker(fileSystem)

    @Override
    TaskOutputPacker getPacker() {
        return tarPacker
    }

    @Unroll
    def "can pack single task output file with file mode #mode"() {
        def sourceOutputFile = Spy(File, constructorArgs: [tempDir.file("source.txt").absolutePath])
        sourceOutputFile << "output"
        def targetOutputFile = Spy(File, constructorArgs: [tempDir.file("target.txt").absolutePath])
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
        def sourceOutputDir = tempDir.file("source").createDir()
        def sourceSubDir = sourceOutputDir.file("subdir").createDir()
        def sourceDataFile = sourceSubDir.file("data.txt")
        sourceDataFile << "output"
        def targetOutputDir = tempDir.file("target").createDir()
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
        def sourceOutput = tempDir.file("source")
        def targetOutput = tempDir.file("target")
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
        def sourceOutputFile = tempDir.file("source.txt")
        sourceOutputFile << "output"
        def targetOutputFile = tempDir.file("target.txt")
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
        def sourceDir = tempDir.file("source")
        def missingSourceFile = sourceDir.file("missing.txt")
        def missingSourceDir = sourceDir.file("missing")
        def targetDir = tempDir.file("target")
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
        def sourceDir = tempDir.file("source").createDir()
        def targetDir = tempDir.file("target")
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

    def "parent directory is created for output file"() {
        def targetOutputFile = tempDir.file("build/some-dir/output.txt")
        targetOutputFile << "Some data"

        when:
        TarTaskOutputPacker.ensureDirectoryForProperty(FILE, targetOutputFile)

        then:
        targetOutputFile.parentFile.assertIsEmptyDir()
    }

    def "directory is created for output directory"() {
        def targetOutputDir = tempDir.file("build/output")

        when:
        TarTaskOutputPacker.ensureDirectoryForProperty(DIRECTORY, targetOutputDir)

        then:
        targetOutputDir.assertIsEmptyDir()
    }

    def "cleans up leftover files in output directory"() {
        def targetOutputDir = tempDir.file("build/output")
        targetOutputDir.file("sub-dir/data.txt") << "Some data"

        when:
        TarTaskOutputPacker.ensureDirectoryForProperty(DIRECTORY, targetOutputDir)

        then:
        targetOutputDir.assertIsEmptyDir()
    }

    def "creates directories even if there is a pre-existing file in its place"() {
        def targetOutputDir = tempDir.file("build/output")
        targetOutputDir << "This should become a directory"

        when:
        TarTaskOutputPacker.ensureDirectoryForProperty(DIRECTORY, targetOutputDir)

        then:
        targetOutputDir.assertIsEmptyDir()
    }

    def "creates parent directories for output file even if there is a pre-existing directory in its place"() {
        def targetOutputFile = tempDir.file("build/some-dir/output.txt")
        targetOutputFile.createDir()

        when:
        TarTaskOutputPacker.ensureDirectoryForProperty(FILE, targetOutputFile)

        then:
        targetOutputFile.parentFile.assertIsEmptyDir()
    }
}
