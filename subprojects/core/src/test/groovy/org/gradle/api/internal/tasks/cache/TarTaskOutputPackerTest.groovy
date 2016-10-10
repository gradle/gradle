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

package org.gradle.api.internal.tasks.cache

import org.gradle.internal.nativeplatform.filesystem.FileSystem
import spock.lang.Unroll

class TarTaskOutputPackerTest extends AbstractTaskOutputPackerSpec {
    def fileSystem = Mock(FileSystem)
    def packer = new TarTaskOutputPacker(fileSystem)

    @Unroll
    def "can pack single task output file with file mode #mode"() {
        def sourceOutputFile = tempDir.file("source.txt")
        sourceOutputFile << "output"
        def targetOutputFile = tempDir.file("target.txt")
        def output = new ByteArrayOutputStream()
        def unixMode = Integer.parseInt(mode, 8)

        when:
        packer.pack(taskOutputs, output)
        then:
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "test", outputFile: sourceOutputFile)
        ] as SortedSet)
        1 * fileSystem.getUnixMode(sourceOutputFile) >> unixMode
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        packer.unpack(taskOutputs, input)

        then:
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "test", outputFile: targetOutputFile)
        ] as SortedSet)
        1 * fileSystem.chmod(targetOutputFile, unixMode)
        then:
        targetOutputFile.text == "output"
        0 * _

        where:
        mode   | _
        "0644" | _
        "0755" | _
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
        packer.pack(taskOutputs, output)
        then:
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: "test", outputFile: sourceOutputDir)
        ] as SortedSet)
        1 * fileSystem.getUnixMode(sourceSubDir) >> 0711
        1 * fileSystem.getUnixMode(sourceDataFile) >> 0600
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        packer.unpack(taskOutputs, input)

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
        def sourceOutputFile = tempDir.file("source-.txt")
        sourceOutputFile << "output"
        def targetOutputFile = tempDir.file("target.txt")
        def output = new ByteArrayOutputStream()

        when:
        packer.pack(taskOutputs, output)
        then:
        noExceptionThrown()
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: propertyName, outputFile: sourceOutputFile)
        ] as SortedSet)
        1 * fileSystem.getUnixMode(sourceOutputFile) >> 0644
        0 * _

        when:
        def input = new ByteArrayInputStream(output.toByteArray())
        packer.unpack(taskOutputs, input)

        then:
        taskOutputs.getFileProperties() >> ([
            new TestProperty(propertyName: propertyName, outputFile: targetOutputFile)
        ] as SortedSet)
        1 * fileSystem.chmod(targetOutputFile, 0644)
        then:
        targetOutputFile.text == "output"
        0 * _
    }
}
