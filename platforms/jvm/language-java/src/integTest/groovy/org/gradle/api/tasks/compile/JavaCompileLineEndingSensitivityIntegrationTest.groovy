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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture

class JavaCompileLineEndingSensitivityIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {
    private static String compileTask = ':compileJava'

    def setup() {
        buildFile << """
            plugins {
                id 'java'
            }
        """
    }

    def "java compile is not sensitive to line endings during up-to-date checks"() {
        writeJavaSourceWithUnixLineEndings()

        when:
        succeeds compileTask

        then:
        executedAndNotSkipped compileTask

        when:
        writeJavaSourceWithWindowsLineEndings()
        succeeds compileTask

        then:
        skipped compileTask
    }

    def "java compile is not sensitive to line endings during build cache key calculation"() {
        writeJavaSourceWithUnixLineEndings()

        when:
        executer.withBuildCacheEnabled()
        succeeds compileTask

        then:
        executedAndNotSkipped compileTask

        when:
        writeJavaSourceWithWindowsLineEndings()
        succeeds "clean"

        and:
        executer.withBuildCacheEnabled()
        succeeds compileTask

        then:
        fromCache compileTask
    }

    void fromCache(String taskPath) {
        assert result.groupedOutput.task(taskPath).outcome == "FROM-CACHE"
    }

    void writeJavaSourceWithUnixLineEndings() {
        file('src/main/java/Foo.java').text = javaSourceWithLineEndings('\n')
    }

    String writeJavaSourceWithWindowsLineEndings() {
        file('src/main/java/Foo.java').text = javaSourceWithLineEndings('\r\n')
    }

    static String javaSourceWithLineEndings(String eol) {
        return "public class Foo {${eol}    int bar;${eol}}${eol}"
    }
}
