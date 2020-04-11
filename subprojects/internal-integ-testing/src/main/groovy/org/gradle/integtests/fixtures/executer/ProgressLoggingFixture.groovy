/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures.executer


import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.internal.logging.LoggingOutputInternal
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile

import javax.inject.Inject

class ProgressLoggingFixture extends InitScriptExecuterFixture {

    private TestFile fixtureData

    ProgressLoggingFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        super(executer, testDir)
    }

    List<String> progressContent

    @Override
    String initScriptContent() {
        fixtureData = testDir.testDirectory.file("progress-fixture.log")
        """
            import ${OutputEventListener.name}
            import ${OutputEvent.name}
            import ${ProgressStartEvent.name}
            import ${ProgressEvent.name}
            import ${ProgressCompleteEvent.name}
            import ${LoggingOutputInternal.name}
            import ${BuildServiceRegistry.name}
            import ${BuildService.name}
            import ${BuildServiceParameters.name}
            import ${Inject.name}

            abstract class OutputProgressService
                implements BuildService<Parameters>, OutputEventListener, AutoCloseable  {

                interface Parameters extends BuildServiceParameters {
                    RegularFileProperty getOutputFile()
                }

                @Inject
                protected abstract LoggingOutput getLoggingOutput()

                OutputProgressService() {
                    (loggingOutput as LoggingOutputInternal).addOutputEventListener(this)
                }

                @Override
                synchronized void close() {
                    (loggingOutput as LoggingOutputInternal).removeOutputEventListener(this)
                }

                @Override
                void onOutput(OutputEvent event) {
                    if (event instanceof ProgressStartEvent) {
                        outputFile << "[START \$event.description]\\n"
                    } else if (event instanceof ProgressEvent) {
                        outputFile << "[\$event.status]\\n"
                    } else if (event instanceof ProgressCompleteEvent) {
                        outputFile << "[END \$event.status]\\n"
                    }
                }

                private File getOutputFile() {
                    parameters.outputFile.get().asFile
                }
            }

            File outputFile = file("${fixtureData.toURI()}")

            def services = gradle.services
            def buildServices = services.get(BuildServiceRegistry)
            def outputProgress = buildServices.registerIfAbsent("outputProgress", OutputProgressService) {
                parameters.outputFile.set(outputFile)
            }

            // forces the service to be initialized immediately
            outputProgress.get()
       """
    }

    @Override
    void afterBuild() {
        if (fixtureData.exists()) {
            progressContent = fixtureData.text.readLines()
            assert fixtureData.delete()
        } else {
            progressContent = []
        }
    }

    boolean downloadProgressLogged(URI url) {
        downloadProgressLogged(url.toASCIIString())
    }

    boolean downloadProgressLogged(String url) {
        return progressLogged("Download $url")
    }

    boolean uploadProgressLogged(URI url) {
        uploadProgressLogged(url.toString())
    }

    boolean uploadProgressLogged(String url) {
        return progressLogged("Upload $url")
    }

    boolean progressLogged(String operation) {
        def lines = progressContent
        def startIndex = lines.indexOf("[START " + operation + "]")
        if (startIndex == -1) {
            return false
        }
        lines = lines[startIndex..<lines.size()]
        lines = lines[0..lines.indexOf("[END " + operation + "]")]
        lines.size() >= 2
    }

    boolean statusLogged(String message) {
        return progressContent.contains("[" + message + "]")
    }

    boolean statusMatches(String regex) {
        return progressContent.any { it.matches("\\[" + regex + "\\]") }
    }
}
