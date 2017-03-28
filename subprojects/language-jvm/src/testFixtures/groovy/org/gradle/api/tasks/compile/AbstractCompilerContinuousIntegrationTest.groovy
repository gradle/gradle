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

import org.gradle.launcher.continuous.Java7RequiringContinuousIntegrationTest

abstract class AbstractCompilerContinuousIntegrationTest extends Java7RequiringContinuousIntegrationTest {

    def setup() {
        executer.withWorkerDaemonsExpirationDisabled()
    }

    def cleanup() {
        gradle.cancel()
    }

    abstract String getCompileTaskName()
    abstract String getCompileTaskType()
    abstract String getSourceFileName()
    abstract String getInitialSourceContent()
    abstract String getChangedSourceContent()
    abstract String getApplyAndConfigure()

    def "reuses compiler daemons across continuous build instances" () {
        def inputFileName = sourceFileName
        def inputFile = file(inputFileName).createFile()
        def compilerDaemonIdentityFileName = "build/compilerId"
        def compilerDaemonIdentityFile = file(compilerDaemonIdentityFileName)

        buildFile << """
            ${applyAndConfigure}

            import org.gradle.workers.internal.WorkerDaemonFactory
            import org.gradle.workers.internal.DaemonForkOptions

            tasks.withType(${compileTaskType}) {
                doLast { task ->
                    def compilerDaemonIdentityFile = file("$compilerDaemonIdentityFileName")
                    def workerDaemonFactory = services.get(WorkerDaemonFactory)
                    compilerDaemonIdentityFile << workerDaemonFactory.clientsManager.allClients.collect { System.identityHashCode(it) }.sort().join(" ") + "\\n"
                }
            }
        """

        when:
        inputFile.text = initialSourceContent

        then:
        succeeds(compileTaskName)

        when:
        inputFile.text = changedSourceContent

        then:
        succeeds()

        and:
        def compilerDaemonSets = compilerDaemonIdentityFile.readLines()
        compilerDaemonSets.size() == 2
        compilerDaemonSets[0].split(" ").size() > 0
        compilerDaemonSets[0] == compilerDaemonSets[1]
    }
}
