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

package org.gradle.configurationcache.inputs.process

import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.exec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.javaexec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.processBuilder
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.runtimeExec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.stringArrayExecute

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
            withProblem("$location '${relativePath(file)}': external process started")
        }

        where:
        snippetsFactory             | file                           | location
        exec().groovy               | "build.gradle"                 | "Build file"
        javaexec().groovy           | "build.gradle"                 | "Build file"
        processBuilder().groovy     | "build.gradle"                 | "Build file"
        stringArrayExecute().groovy | "build.gradle"                 | "Build file"
        runtimeExec().groovy        | "build.gradle"                 | "Build file"
        exec().kotlin               | "build.gradle.kts"             | "Build file"
        javaexec().kotlin           | "build.gradle.kts"             | "Build file"
        processBuilder().kotlin     | "build.gradle.kts"             | "Build file"
        stringArrayExecute().kotlin | "build.gradle.kts"             | "Build file"
        runtimeExec().kotlin        | "build.gradle.kts"             | "Build file"
        exec().groovy               | "buildSrc/build.gradle"        | "Build file"
        javaexec().groovy           | "buildSrc/build.gradle"        | "Build file"
        processBuilder().groovy     | "buildSrc/build.gradle"        | "Build file"
        stringArrayExecute().groovy | "buildSrc/build.gradle"        | "Build file"
        runtimeExec().groovy        | "buildSrc/build.gradle"        | "Build file"
        exec().kotlin               | "buildSrc/build.gradle.kts"    | "Build file"
        javaexec().kotlin           | "buildSrc/build.gradle.kts"    | "Build file"
        processBuilder().kotlin     | "buildSrc/build.gradle.kts"    | "Build file"
        stringArrayExecute().kotlin | "buildSrc/build.gradle.kts"    | "Build file"
        runtimeExec().kotlin        | "buildSrc/build.gradle.kts"    | "Build file"
        exec().groovy               | "buildSrc/settings.gradle"     | "Settings file"
        javaexec().groovy           | "buildSrc/settings.gradle"     | "Settings file"
        processBuilder().groovy     | "buildSrc/settings.gradle"     | "Settings file"
        stringArrayExecute().groovy | "buildSrc/settings.gradle"     | "Settings file"
        runtimeExec().groovy        | "buildSrc/settings.gradle"     | "Settings file"
        exec().kotlin               | "buildSrc/settings.gradle.kts" | "Settings file"
        javaexec().kotlin           | "buildSrc/settings.gradle.kts" | "Settings file"
        processBuilder().kotlin     | "buildSrc/settings.gradle.kts" | "Settings file"
        stringArrayExecute().kotlin | "buildSrc/settings.gradle.kts" | "Settings file"
        runtimeExec().kotlin        | "buildSrc/settings.gradle.kts" | "Settings file"
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
            withProblem("Settings file '${relativePath(file)}': external process started")
        }

        where:
        snippetsFactory             | file                           | location
        exec().groovy               | "settings.gradle"              | "Settings file"
        javaexec().groovy           | "settings.gradle"              | "Settings file"
        processBuilder().groovy     | "settings.gradle"              | "Settings file"
        stringArrayExecute().groovy | "settings.gradle"              | "Settings file"
        runtimeExec().groovy        | "settings.gradle"              | "Settings file"
        exec().kotlin               | "settings.gradle.kts"          | "Settings file"
        javaexec().kotlin           | "settings.gradle.kts"          | "Settings file"
        processBuilder().kotlin     | "settings.gradle.kts"          | "Settings file"
        stringArrayExecute().kotlin | "settings.gradle.kts"          | "Settings file"
        runtimeExec().kotlin        | "settings.gradle.kts"          | "Settings file"
    }
}
