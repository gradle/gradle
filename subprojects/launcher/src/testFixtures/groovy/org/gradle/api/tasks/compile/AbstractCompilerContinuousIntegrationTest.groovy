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
    def cleanup() {
        gradle.cancel()
    }

    abstract String getCompileTaskName()
    abstract String getCompileTaskType()
    abstract String getOptionsProperty()
    abstract String getSourceFileName()
    abstract String getInitialSourceContent()
    abstract String getChangedSourceContent()
    abstract String getApplyAndConfigure()
    abstract String getSharedPackages()

    def "reuses compiler daemon across continuous build instances" () {
        def inputFileName = sourceFileName
        def inputFile = file(inputFileName).createFile()
        def compilerDaemonIdentityFileName = "build/compilerId"
        def compilerDaemonIdentityFile = file(compilerDaemonIdentityFileName)

        buildFile << """
            ${applyAndConfigure}

            import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager
            import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions

            tasks.withType(${compileTaskType}) {
                doLast { task ->
                    def compilerDaemonIdentityFile = file("$compilerDaemonIdentityFileName")
                    def daemonFactory = services.get(CompilerDaemonManager)
                    // this will only get an existing compiler daemon (i.e. it won't create a new one)
                    def compilerDaemonClient = daemonFactory.clientsManager.reserveIdleClient(
                        new DaemonForkOptions(
                            ${optionsProperty}.forkOptions.getMemoryInitialSize(), ${optionsProperty}.forkOptions.getMemoryMaximumSize(), ${optionsProperty}.forkOptions.getJvmArgs(),
                            Collections.<File>emptyList(), [ ${sharedPackages} ])
                    )
                    // if there are no compiler daemons available for the forkOptions in this task, then something's wrong
                    if (compilerDaemonClient == null) {
                        throw new Exception("Could not reserve existing idle compiler daemon client using settings from ${compileTaskName} task!")
                    }
                    compilerDaemonIdentityFile << compilerDaemonClient.hashCode() + "\\n"
                    daemonFactory.clientsManager.release(compilerDaemonClient)
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
        def compilerDaemons = compilerDaemonIdentityFile.readLines()
        compilerDaemons.size() == 2
        compilerDaemons[0] == compilerDaemons[1]
    }
}
