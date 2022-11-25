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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest

abstract class AbstractCompilerContinuousIntegrationTest extends AbstractContinuousIntegrationTest {

    def setup() {
        executer.withWorkerDaemonsExpirationDisabled()
    }

    abstract String getCompileTaskName()
    abstract String getCompileTaskType()
    abstract String getSourceFileName()
    abstract String getInitialSourceContent()
    abstract String getChangedSourceContent()
    abstract String getApplyAndConfigure()

    String getVerifyDaemonsTask() {
        """
            task verifyDaemons {
                doLast {
                    assert services.get(WorkerDaemonClientsManager).allClients.size() == 0
                }
            }
"""
    }

    def "reuses compiler daemons across continuous build instances" () {
        def inputFileName = sourceFileName
        def inputFile = file(inputFileName).createFile()
        def compilerDaemonIdentityFileName = "build/compilerId"
        def compilerDaemonIdentityFile = file(compilerDaemonIdentityFileName)

        buildFile << """
            ${applyAndConfigure}

            import org.gradle.workers.internal.WorkerDaemonClientsManager
            import org.gradle.workers.internal.DaemonForkOptions

            tasks.withType(${compileTaskType}) {
                def compilerDaemonIdentityFile = file("$compilerDaemonIdentityFileName")
                doLast { task ->
                    compilerDaemonIdentityFile << services.get(WorkerDaemonClientsManager).allClients.collect { System.identityHashCode(it) }.sort().join(" ") + "\\n"
                }
            }

            ${verifyDaemonsTask}
        """

        when:
        inputFile.text = initialSourceContent

        then:
        succeeds(compileTaskName)

        when:
        inputFile.text = changedSourceContent

        then:
        buildTriggeredAndSucceeded()

        and:
        def compilerDaemonSets = compilerDaemonIdentityFile.readLines()
        compilerDaemonSets.size() == 2
        compilerDaemonSets[0].split(" ").size() > 0
        compilerDaemonSets[0] == compilerDaemonSets[1]

        when:
        sendEOT()

        then:
        cancelsAndExits()

        and:
        succeeds("verifyDaemons")
    }
}
