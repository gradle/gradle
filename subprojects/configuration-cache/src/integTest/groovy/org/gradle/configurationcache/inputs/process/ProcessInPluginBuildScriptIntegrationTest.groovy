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

class ProcessInPluginBuildScriptIntegrationTest extends AbstractProcessIntegrationTest {
    def "using #snippetsFactory.summary in included plugin settings #file is a problem"() {
        given:
        settingsFile("""
            pluginManagement {
                includeBuild('included')
            }
        """)

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
            if (file.endsWith(".gradle.kts")) {
                withProblem("Settings file '${relativePath(file)}': external process started")
            } else {
                withProblem("Settings file '${relativePath(file)}': line 5: external process started")
            }
        }

        where:
        snippetsFactory             | file
        exec().groovy               | "included/settings.gradle"
        javaexec().groovy           | "included/settings.gradle"
        processBuilder().groovy     | "included/settings.gradle"
        stringArrayExecute().groovy | "included/settings.gradle"
        runtimeExec().groovy        | "included/settings.gradle"
        exec().kotlin               | "included/settings.gradle.kts"
        javaexec().kotlin           | "included/settings.gradle.kts"
        processBuilder().kotlin     | "included/settings.gradle.kts"
        stringArrayExecute().kotlin | "included/settings.gradle.kts"
        runtimeExec().kotlin        | "included/settings.gradle.kts"
    }

    def "using #snippetsFactory.summary in included plugin build #file is a problem"() {
        given:
        settingsFile("""
            pluginManagement {
                includeBuild('included')
            }
        """)

        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        def includedBuildFile = testDirectory.file(file)
        includedBuildFile << """
            ${snippets.imports}
            plugins {
                id("groovy-gradle-plugin")
            }
            ${snippets.body}
        """
        testDirectory.file("included/src/main/groovy/test-convention-plugin.gradle") << """
            println("Applied script plugin")
        """

        buildFile("""
            plugins {
                id("test-convention-plugin")
            }
        """)
        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            if (file.endsWith(".gradle.kts")) {
                withProblem("Build file '${relativePath(file)}': external process started")
            } else {
                withProblem("Build file '${relativePath(file)}': line 8: external process started")
            }
        }

        where:
        snippetsFactory             | file
        exec().groovy               | "included/build.gradle"
        javaexec().groovy           | "included/build.gradle"
        processBuilder().groovy     | "included/build.gradle"
        stringArrayExecute().groovy | "included/build.gradle"
        runtimeExec().groovy        | "included/build.gradle"
        exec().kotlin               | "included/build.gradle.kts"
        javaexec().kotlin           | "included/build.gradle.kts"
        processBuilder().kotlin     | "included/build.gradle.kts"
        stringArrayExecute().kotlin | "included/build.gradle.kts"
        runtimeExec().kotlin        | "included/build.gradle.kts"
    }

}
