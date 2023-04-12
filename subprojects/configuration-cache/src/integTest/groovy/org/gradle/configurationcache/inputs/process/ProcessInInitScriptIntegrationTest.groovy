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

class ProcessInInitScriptIntegrationTest extends AbstractProcessIntegrationTest {
    def "using #snippetsFactory.summary in initialization script #file is a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)

        def initScriptFile = testDirectory.file(file)
        initScriptFile << """
            ${snippets.imports}
            ${snippets.body}
        """
        executer.usingInitScript(initScriptFile)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("Initialization script '${relativePath(file)}': external process started")
        }

        where:
        snippetsFactory             | file
        exec().groovy               | "exec.init.gradle"
        javaexec().groovy           | "exec.init.gradle"
        processBuilder().groovy     | "exec.init.gradle"
        stringArrayExecute().groovy | "exec.init.gradle"
        runtimeExec().groovy        | "exec.init.gradle"
        exec().kotlin               | "exec.init.gradle.kts"
        javaexec().kotlin           | "exec.init.gradle.kts"
        processBuilder().kotlin     | "exec.init.gradle.kts"
        stringArrayExecute().kotlin | "exec.init.gradle.kts"
        runtimeExec().kotlin        | "exec.init.gradle.kts"
    }
}
