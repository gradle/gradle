/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.cc.impl.inputs.process

import org.gradle.test.fixtures.Flaky

import static org.gradle.internal.cc.impl.fixtures.ExternalProcessFixture.exec
import static org.gradle.internal.cc.impl.fixtures.ExternalProcessFixture.javaexec
import static org.gradle.internal.cc.impl.fixtures.ExternalProcessFixture.processBuilder
import static org.gradle.internal.cc.impl.fixtures.ExternalProcessFixture.runtimeExec
import static org.gradle.internal.cc.impl.fixtures.ExternalProcessFixture.stringArrayExecute

@Flaky(because = "https://github.com/gradle/gradle-private/issues/4440")
class ProcessInBuildScriptIntegrationTest extends AbstractProcessIntegrationTest {
    def "using #snippetsFactory.summary in #location.toLowerCase() #file is a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file(file) << """
            ${snippets.imports}
            ${snippets.body}
        """

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("${location.replace('/', File.separator)}: external process started")
        }

        where:
        snippetsFactory             | file                           | location
        exec().groovy               | "build.gradle"                 | "Build file 'build.gradle': line 5"
        javaexec().groovy           | "build.gradle"                 | "Build file 'build.gradle': line 5"
        processBuilder().groovy     | "build.gradle"                 | "Build file 'build.gradle': line 5"
        stringArrayExecute().groovy | "build.gradle"                 | "Build file 'build.gradle': line 5"
        runtimeExec().groovy        | "build.gradle"                 | "Build file 'build.gradle': line 5"
        exec().kotlin               | "build.gradle.kts"             | "Build file 'build.gradle.kts'"
        javaexec().kotlin           | "build.gradle.kts"             | "Build file 'build.gradle.kts'"
        processBuilder().kotlin     | "build.gradle.kts"             | "Build file 'build.gradle.kts'"
        stringArrayExecute().kotlin | "build.gradle.kts"             | "Build file 'build.gradle.kts'"
        runtimeExec().kotlin        | "build.gradle.kts"             | "Build file 'build.gradle.kts'"
        exec().groovy               | "buildSrc/build.gradle"        | "Build file 'buildSrc/build.gradle': line 5"
        javaexec().groovy           | "buildSrc/build.gradle"        | "Build file 'buildSrc/build.gradle': line 5"
        processBuilder().groovy     | "buildSrc/build.gradle"        | "Build file 'buildSrc/build.gradle': line 5"
        stringArrayExecute().groovy | "buildSrc/build.gradle"        | "Build file 'buildSrc/build.gradle': line 5"
        runtimeExec().groovy        | "buildSrc/build.gradle"        | "Build file 'buildSrc/build.gradle': line 5"
        exec().kotlin               | "buildSrc/build.gradle.kts"    | "Build file 'buildSrc/build.gradle.kts'"
        javaexec().kotlin           | "buildSrc/build.gradle.kts"    | "Build file 'buildSrc/build.gradle.kts'"
        processBuilder().kotlin     | "buildSrc/build.gradle.kts"    | "Build file 'buildSrc/build.gradle.kts'"
        stringArrayExecute().kotlin | "buildSrc/build.gradle.kts"    | "Build file 'buildSrc/build.gradle.kts'"
        runtimeExec().kotlin        | "buildSrc/build.gradle.kts"    | "Build file 'buildSrc/build.gradle.kts'"
        exec().groovy               | "buildSrc/settings.gradle"     | "Settings file 'buildSrc/settings.gradle': line 5"
        javaexec().groovy           | "buildSrc/settings.gradle"     | "Settings file 'buildSrc/settings.gradle': line 5"
        processBuilder().groovy     | "buildSrc/settings.gradle"     | "Settings file 'buildSrc/settings.gradle': line 5"
        stringArrayExecute().groovy | "buildSrc/settings.gradle"     | "Settings file 'buildSrc/settings.gradle': line 5"
        runtimeExec().groovy        | "buildSrc/settings.gradle"     | "Settings file 'buildSrc/settings.gradle': line 5"
        exec().kotlin               | "buildSrc/settings.gradle.kts" | "Settings file 'buildSrc/settings.gradle.kts'"
        javaexec().kotlin           | "buildSrc/settings.gradle.kts" | "Settings file 'buildSrc/settings.gradle.kts'"
        processBuilder().kotlin     | "buildSrc/settings.gradle.kts" | "Settings file 'buildSrc/settings.gradle.kts'"
        stringArrayExecute().kotlin | "buildSrc/settings.gradle.kts" | "Settings file 'buildSrc/settings.gradle.kts'"
        runtimeExec().kotlin        | "buildSrc/settings.gradle.kts" | "Settings file 'buildSrc/settings.gradle.kts'"
    }

    def "using #snippetsFactory.summary in settings file #file is a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file(file) << """
            ${snippets.imports}

            ${snippets.body}
        """

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("${location.replace('/', File.separator)}: external process started")
        }

        where:
        snippetsFactory             | file                           | location
        exec().groovy               | "settings.gradle"              | "Settings file 'settings.gradle': line 6"
        javaexec().groovy           | "settings.gradle"              | "Settings file 'settings.gradle': line 6"
        processBuilder().groovy     | "settings.gradle"              | "Settings file 'settings.gradle': line 6"
        stringArrayExecute().groovy | "settings.gradle"              | "Settings file 'settings.gradle': line 6"
        runtimeExec().groovy        | "settings.gradle"              | "Settings file 'settings.gradle': line 6"
        exec().kotlin               | "settings.gradle.kts"          | "Settings file 'settings.gradle.kts'"
        javaexec().kotlin           | "settings.gradle.kts"          | "Settings file 'settings.gradle.kts'"
        processBuilder().kotlin     | "settings.gradle.kts"          | "Settings file 'settings.gradle.kts'"
        stringArrayExecute().kotlin | "settings.gradle.kts"          | "Settings file 'settings.gradle.kts'"
        runtimeExec().kotlin        | "settings.gradle.kts"          | "Settings file 'settings.gradle.kts'"
    }
}
