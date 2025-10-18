/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.internal

import com.google.common.io.ByteSource
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import spock.lang.Specification

import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SKIPPED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class DefaultBuildResultTest extends Specification {
    BuildTask successBuildResult = new DefaultBuildTask(':a', SUCCESS)
    BuildTask failedBuildResult = new DefaultBuildTask(':b', FAILED)
    BuildTask skippedBuildResult = new DefaultBuildTask(':c', SKIPPED)
    def buildTasks = [successBuildResult, failedBuildResult]
    DefaultBuildResult defaultBuildResult = new DefaultBuildResult('output', buildTasks)

    def "provides expected field values"() {
        expect:
        defaultBuildResult.output == 'output'
        defaultBuildResult.tasks == buildTasks
        defaultBuildResult.tasks(SUCCESS) == [successBuildResult]
        defaultBuildResult.tasks(FAILED) == [failedBuildResult]
        defaultBuildResult.taskPaths(SUCCESS) == [successBuildResult.path]
        defaultBuildResult.taskPaths(FAILED) == [failedBuildResult.path]
    }

    def "returned tasks are unmodifiable"() {
        when:
        defaultBuildResult.tasks << skippedBuildResult

        then:
        thrown(UnsupportedOperationException)

        when:
        defaultBuildResult.tasks(SUCCESS) << skippedBuildResult

        then:
        thrown(UnsupportedOperationException)

        when:
        defaultBuildResult.taskPaths(SUCCESS) << skippedBuildResult

        then:
        thrown(UnsupportedOperationException)
    }

    def "getOutput() and getOutputReader() returns the same output"() {
        String source = """
            line1
            line2
            line3
            line4
            line5
            """.stripIndent().trim()
        BuildResult buildResult = new DefaultBuildResult(source, [])

        when:
        buildResult.output == source

        then:
        try (BufferedReader outputReader = buildResult.outputReader) {
            outputReader.readLines() == buildResult.output.lines()
        }
    }

    def "when result has no output, expect result output is empty"() {
        BuildResult buildResult = new DefaultBuildResult(emptyByteSource(), [])

        expect:
        buildResult.output == ""
        buildResult.outputReader.readLines() == []
    }

    def "each OutputReader returned by getOutputReader() returns different instances"() {
        String source = """
            line1
            line2
            line3
            line4
            line5
            """.stripIndent().trim()
        BuildResult buildResult = new DefaultBuildResult(source, [])
        def reader1 = buildResult.outputReader
        def reader2 = buildResult.outputReader

        expect:
        reader1 != reader2
        reader1.readLines() == ["line1", "line2", "line3", "line4", "line5"]
        reader2.readLines() == ["line1", "line2", "line3", "line4", "line5"]
    }

    def "the BufferedReader returned by getOutputReader() lazily read data from source"() {
        // Here we test only the first 8192 characters of the output are read.
        // (Assuming the returned BufferedReader has a default buffer size of 8192.)
        // All build output must not be read entirely, otherwise the entire output would be loaded into memory,
        // which would be wasteful.

        final int lineSize = 100
        final int defaultBufferSize = 8192
        int inputStreamReadCount = 0

        final InputStream source = new InputStream() {
            @Override
            int read() throws IOException {
                if (inputStreamReadCount > defaultBufferSize) {
                    throw new IllegalStateException("input should not be read exhaustively")
                }
                inputStreamReadCount++
                if (inputStreamReadCount % lineSize == 0) {
                    return '\n' as char
                } else {
                    return 'a' as char
                }
            }
        }

        final ByteSource byteSource = new ByteSource() {
            @Override
            InputStream openStream() throws IOException {
                return source
            }
        }

        final BuildResult buildResult = new DefaultBuildResult(byteSource, buildTasks)

        expect:
        try (BufferedReader outputReader = buildResult.outputReader) {
            outputReader.readLine() == "a" * lineSize
        }

        inputStreamReadCount == defaultBufferSize
    }

    static ByteSource emptyByteSource() {
        return new ByteSource() {
            @Override
            InputStream openStream() throws IOException {
                return InputStream.nullInputStream()
            }
        }
    }
}
