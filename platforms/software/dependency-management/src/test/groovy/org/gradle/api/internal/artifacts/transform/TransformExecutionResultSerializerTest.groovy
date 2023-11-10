/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableList
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory
class TransformExecutionResultSerializerTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    def inputArtifact = file("inputArtifact").createDir()
    def outputDir = file("outputDir")
    def resultFile = file("results.txt")
    def serializer = new TransformExecutionResultSerializer()

    def "reads and writes transformation results"() {
        expect:
        assertCanWriteAndReadResult(
            inputArtifact.file("inside"),
            inputArtifact,
            outputDir.file("first"),
            outputDir.file("second"),
            outputDir
        )
    }

    def "reads and writes output only transformation results"() {
        expect:
        assertCanWriteAndReadResult(
            outputDir.file("first"),
            outputDir.file("second"),
            outputDir
        )
    }

    def "reads and writes input only transformation results"() {
        expect:
        assertCanWriteAndReadResult(
            inputArtifact.file("inside"),
            inputArtifact,
            inputArtifact
        )
    }

    def "resolves files in input artifact relative to input artifact"() {
        def newInputArtifact = file("newInputArtifact").createDir()

        ImmutableList<File> resultFiles = ImmutableList.of(
            inputArtifact.file("inside"),
            inputArtifact,
            outputDir,
            inputArtifact
        )
        ImmutableList<File> resultResolvedForNewInputArtifact = ImmutableList.of(
            newInputArtifact.file("inside"),
            newInputArtifact,
            outputDir,
            newInputArtifact
        )

        when:
        def initialResults = buildTransformExecutionResult(resultFiles)
        serializer.writeToFile(resultFile, initialResults)
        then:
        resultFile.exists()
        initialResults.bindToOutputDir(outputDir).resolveOutputsForInputArtifact(inputArtifact) == resultFiles
        initialResults.bindToOutputDir(outputDir).resolveOutputsForInputArtifact(newInputArtifact) == resultResolvedForNewInputArtifact

        when:
        def loadedResults = serializer.readResultsFile(resultFile)
        then:
        loadedResults.bindToOutputDir(outputDir).resolveOutputsForInputArtifact(inputArtifact) == resultFiles
        loadedResults.bindToOutputDir(outputDir).resolveOutputsForInputArtifact(newInputArtifact) == resultResolvedForNewInputArtifact
    }

    def "loads files in output directory relative to output directory"() {
        def newOutputDir = file("newOutputDir").createDir()

        ImmutableList<File> resultFiles = ImmutableList.of(
            inputArtifact,
            outputDir,
            outputDir.file("output.txt")
        )
        ImmutableList<File> resultInNewOutputDir = ImmutableList.of(
            inputArtifact,
            newOutputDir,
            newOutputDir.file("output.txt")
        )

        when:
        def initialResults = buildTransformExecutionResult(resultFiles)
        serializer.writeToFile(resultFile, initialResults)
        then:
        resultFile.exists()
        initialResults.bindToOutputDir(outputDir).resolveOutputsForInputArtifact(inputArtifact) == resultFiles

        when:
        def loadedResults = serializer.readResultsFile(resultFile)
        then:
        loadedResults.bindToOutputDir(newOutputDir).resolveOutputsForInputArtifact(inputArtifact) == resultInNewOutputDir
    }

    private void assertCanWriteAndReadResult(File... files) {
        ImmutableList<File> resultFiles = ImmutableList.<File>builder().add(files).build()
        def initialResults = buildTransformExecutionResult(resultFiles)
        assert initialResults.bindToOutputDir(outputDir).resolveOutputsForInputArtifact(inputArtifact) == resultFiles

        serializer.writeToFile(resultFile, initialResults)
        assert resultFile.exists()
        assert serializer.readResultsFile(resultFile).bindToOutputDir(outputDir).resolveOutputsForInputArtifact(inputArtifact) == resultFiles
    }

    private TransformExecutionResult buildTransformExecutionResult(Collection<File> files) {
        def builder = TransformExecutionResult.builderFor(inputArtifact, outputDir)
        for (File file in files) {
            builder.addOutput(file) {}
        }
        return builder.build()
    }

    TestFile file(String path) {
        temporaryFolder.file(path)
    }
}
