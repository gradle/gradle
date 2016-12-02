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

import org.gradle.caching.internal.tasks.origin.TaskOutputOriginReader
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginWriter
import org.gradle.internal.nativeplatform.filesystem.FileSystem
import spock.lang.Unroll

import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.DIRECTORY
import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.FILE

class TarTaskOutputPackerTest extends AbstractTaskOutputPackerSpec {
    def fileSystem = Mock(FileSystem)
    def readOrigin = Stub(TaskOutputOriginReader)
    def writeOrigin = Stub(TaskOutputOriginWriter)
    def packer = new TarTaskOutputPacker(fileSystem)

    @Unroll
    def "can pack single task output file with file mode #mode"() {
        def sourceOutputFile = Spy(File, constructorArgs: [tempDir.file("source.txt").absolutePath])
        sourceOutputFile << "output"
        def targetOutputFile = Spy(File, constructorArgs: [tempDir.file("target.txt").absolutePath])
        def output = new ByteArrayOutputStream()
        def unixMode = Integer.parseInt(mode, 8)

        when:
        packer.pack(taskOutputs, output, writeOrigin)
        then:
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "test", outputFile: sourceOutputFile)
        ] as SortedSet)
        1 * fileSystem.getUnixMode(sourceOutputFile) >> unixMode
        _ * sourceOutputFile.lastModified() >> fileDate
        _ * sourceOutputFile._
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        packer.unpack(taskOutputs, input, readOrigin)

        then:
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "test", outputFile: targetOutputFile)
        ] as SortedSet)
        1 * fileSystem.chmod(targetOutputFile, unixMode)
        1 * targetOutputFile.setLastModified(_) >> { long time ->
            assert time == fileDate
            return true
        }
        _ * targetOutputFile._
        then:
        targetOutputFile.text == "output"
        0 * _

        where:
        mode   | fileDate
        "0644" | 123456789000L
        "0755" | 123456789012L
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
        packer.pack(taskOutputs, output, writeOrigin)
        then:
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "test", outputFile: sourceOutputDir)
        ] as SortedSet)
        1 * fileSystem.getUnixMode(sourceSubDir) >> 0711
        1 * fileSystem.getUnixMode(sourceDataFile) >> 0600
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        packer.unpack(taskOutputs, input, readOrigin)

        then:
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "test", outputFile: targetOutputDir)
        ] as SortedSet)
        1 * fileSystem.chmod(targetOutputDir, 0755)
        1 * fileSystem.chmod(targetSubDir, 0711)
        1 * fileSystem.chmod(targetDataFile, 0600)
        then:
        targetDataFile.text == "output"
        0 * _
    }

    def "can pack single task output file with long name"() {
        def propertyName = "prop-" + ("x" * 100)
        def sourceOutputFile = tempDir.file("source.txt")
        sourceOutputFile << "output"
        def targetOutputFile = tempDir.file("target.txt")
        def output = new ByteArrayOutputStream()
        when:
        packer.pack(taskOutputs, output, writeOrigin)
        then:
        noExceptionThrown()
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: propertyName, outputFile: sourceOutputFile)
        ] as SortedSet)
        1 * fileSystem.getUnixMode(sourceOutputFile) >> 0644
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        packer.unpack(taskOutputs, input, readOrigin)

        then:
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: propertyName, outputFile: targetOutputFile)
        ] as SortedSet)
        1 * fileSystem.chmod(targetOutputFile, 0644)
        then:
        targetOutputFile.text == "output"
        0 * _
    }

    def "can pack task output with all optional, empty outputs"() {
        def output = new ByteArrayOutputStream()
        when:
        packer.pack(taskOutputs, output, writeOrigin)
        then:
        noExceptionThrown()
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "out1", outputFile: null),
            new TestProperty(propertyName: "out2", outputFile: null)
        ] as SortedSet)
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        packer.unpack(taskOutputs, input, readOrigin)

        then:
        noExceptionThrown()
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "out1", outputFile: null),
            new TestProperty(propertyName: "out2", outputFile: null)
        ] as SortedSet)
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
        packer.pack(taskOutputs, output, writeOrigin)
        then:
        noExceptionThrown()
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "missingFile", outputFile: missingSourceFile, outputType: FILE),
            new TestProperty(propertyName: "missingDir", outputFile: missingSourceDir, outputType: DIRECTORY)
        ] as SortedSet)
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        packer.unpack(taskOutputs, input, readOrigin)

        then:
        noExceptionThrown()
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "missingFile", outputFile: missingTargetFile, outputType: FILE),
            new TestProperty(propertyName: "missingDir", outputFile: missingTargetDir, outputType: DIRECTORY)
        ] as SortedSet)
        0 * _
    }

    def "can pack task output with empty output directory"() {
        def sourceDir = tempDir.file("source").createDir()
        def targetDir = tempDir.file("target")
        def output = new ByteArrayOutputStream()
        when:
        packer.pack(taskOutputs, output, writeOrigin)
        then:
        noExceptionThrown()
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "empty", outputFile: sourceDir, outputType: DIRECTORY)
        ] as SortedSet)
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        packer.unpack(taskOutputs, input, readOrigin)

        then:
        noExceptionThrown()
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "empty", outputFile: targetDir, outputType: DIRECTORY),
        ] as SortedSet)
        1 * fileSystem.chmod(targetDir, 0755)
        then:
        targetDir.assertIsEmptyDir()
        0 * _
    }
}
