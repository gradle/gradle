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

package org.gradle.caching.internal.tasks

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.changedetection.TaskArtifactState
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType
import org.gradle.api.internal.tasks.ResolvedTaskOutputFilePropertySpec
import org.gradle.api.internal.tasks.execution.TaskOutputsGenerationListener
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginFactory
import org.gradle.caching.internal.tasks.origin.TaskOutputOriginMetadata
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testing.internal.util.Specification
import org.junit.Rule
import org.gradle.internal.time.Timer;

import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.FILE

@CleanupTestDirectory
class TaskOutputCacheCommandFactoryTest extends Specification {
    def packer = Mock(TaskOutputPacker)
    def originFactory = Mock(TaskOutputOriginFactory)
    def commandFactory = new TaskOutputCacheCommandFactory(packer, originFactory)

    def key = Mock(TaskOutputCachingBuildCacheKey)
    def task = Mock(TaskInternal)
    def taskOutputsGenerationListener = Mock(TaskOutputsGenerationListener)
    def taskArtifactState = Mock(TaskArtifactState)
    def timer = Stub(Timer)

    def originMetadata = Mock(TaskOutputOriginMetadata)

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def "load unpacks metadata"() {
        def input = Mock(InputStream)
        def outputProperties = prop("output")
        def load = commandFactory.createLoad(key, outputProperties, task, taskOutputsGenerationListener, taskArtifactState, timer)

        when:
        def result = load.load(input)

        then:
        1 * taskOutputsGenerationListener.beforeTaskOutputsGenerated()
        1 * originFactory.createReader(task)

        then:
        1 * packer.unpack(outputProperties, input, _) >> new TaskOutputPacker.UnpackResult(originMetadata, 123)

        then:
        result.artifactEntryCount == 123
        result.metadata == originMetadata
        0 * _
    }

    def "after failed unpacking output is cleaned up"() {
        def input = Mock(InputStream)
        def outputFile = temporaryFolder.file("output.txt")
        def outputProperties = prop("output", FILE, outputFile)
        def command = commandFactory.createLoad(key, outputProperties, task, taskOutputsGenerationListener, taskArtifactState, timer)

        when:
        command.load(input)

        then:
        1 * taskOutputsGenerationListener.beforeTaskOutputsGenerated()
        1 * originFactory.createReader(task)

        then:
        1 * packer.unpack(outputProperties, input, _) >> {
            outputFile << "partially extracted output fil..."
            throw new RuntimeException("unpacking error")
        }

        then:
        1 * taskArtifactState.afterOutputsRemovedBeforeTask()

        then:
        def ex = thrown Exception
        !(ex instanceof UnrecoverableTaskOutputUnpackingException)
        ex.cause.message == "unpacking error"
        !outputFile.exists()
        0 * _
    }

    def "unrecoverable error during cleanup of failed unpacking is reported"() {
        def input = Mock(InputStream)
        def outputProperties = Mock(SortedSet)
        def command = commandFactory.createLoad(key, outputProperties, task, taskOutputsGenerationListener, taskArtifactState, timer)

        when:
        command.load(input)

        then:
        1 * taskOutputsGenerationListener.beforeTaskOutputsGenerated()
        1 * originFactory.createReader(task)

        then:
        1 * packer.unpack(outputProperties, input, _) >> {
            throw new RuntimeException("unpacking error")
        }

        then:
        1 * outputProperties.iterator() >> { throw new RuntimeException("cleanup error") }

        then:
        def ex = thrown UnrecoverableTaskOutputUnpackingException
        ex.cause.message == "unpacking error"
        0 * _
    }

    def "store invokes packer"() {
        def output = Mock(OutputStream)
        def outputProperties = prop("output")
        def command = commandFactory.createStore(key, outputProperties, task, timer)

        when:
        def result = command.store(output)

        then:
        1 * originFactory.createWriter(task, _)

        then:
        1 * packer.pack(outputProperties, output, _) >> new TaskOutputPacker.PackResult(123)

        then:
        result.artifactEntryCount == 123
        0 * _
    }

    def prop(String name, OutputType outputType = FILE, File outputFile = null) {
        return [new ResolvedTaskOutputFilePropertySpec(name, outputType, outputFile)] as SortedSet
    }
}
