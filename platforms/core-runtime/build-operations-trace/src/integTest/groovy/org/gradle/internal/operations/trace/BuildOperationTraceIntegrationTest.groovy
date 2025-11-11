/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.operations.trace


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

class BuildOperationTraceIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "no tree files are produced by default"() {
        when:
        run "help", "-D${BuildOperationTrace.SYSPROP}=trace"

        then:
        file("trace-log.txt").exists()
        !file("trace-tree.txt").exists()
        !file("trace-tree.json").exists()
    }

    def "no trace files are produced when trace parameter is false"() {
        when:
        run "help", "-D${BuildOperationTrace.SYSPROP}=false"

        then:
        testDirectory.listFiles().findAll { it.name.endsWith("-log.txt") } == []
    }

    def "trace files are written to absolute path when absolute path is provided"() {
        given:
        def absolutePath = tmpDir.file("custom-trace").absolutePath

        when:
        run "help", "-D${BuildOperationTrace.TREE_SYSPROP}=true", "-D${BuildOperationTrace.SYSPROP}=$absolutePath"

        then:
        tmpDir.file("custom-trace-log.txt").exists()
        tmpDir.file("custom-trace-tree.txt").exists()
        tmpDir.file("custom-trace-tree.json").exists()
    }

    def "trace files are relative to the current directory when parameter is #description"() {
        when:
        run "help", "-D${BuildOperationTrace.TREE_SYSPROP}=true", "-D${BuildOperationTrace.SYSPROP}=$trace"

        then:
        file("$output-log.txt").exists()
        file("$output-tree.txt").exists()
        file("$output-tree.json").exists()

        where:
        description       | trace          | output
        "empty"           | ""             | "operations"
        "a file name"     | "custom"       | "custom"
        "a relative path" | "build/custom" | "build/custom"
    }

}
