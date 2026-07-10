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
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.logging.LoggingOutputInternal
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile

import javax.inject.Inject

/**
 * @deprecated should use build operations fixture instead.
 */
@Deprecated
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
            import ${BuildEventListenerRegistryInternal.name}
            import ${BuildService.name}
            import ${BuildServiceParameters.name}
            import ${BuildOperationListener.name}
            import ${BuildOperationDescriptor.name}
            import ${OperationIdentifier.name}
            import ${OperationStartEvent.name}
            import ${OperationProgressEvent.name}
            import ${OperationFinishEvent.name}
            import ${Inject.name}

            interface InternalServices {
                @Inject LoggingOutputInternal getLoggingOutput()
            }

            abstract class OutputProgressService
                implements BuildService<Parameters>, OutputEventListener, AutoCloseable, BuildOperationListener {

                interface Parameters extends BuildServiceParameters {
                    RegularFileProperty getOutputFile()
                }

                @Inject
                protected abstract ObjectFactory getObjects()

                private LoggingOutput loggingOutput

                OutputProgressService() {
                    // Ensure parameters.outputFile is set before adding ourselves as a listener
                    if (!parameters.outputFile.present) {
                        throw new IllegalStateException("parameters.outputFile is not set")
                    }
                    loggingOutput = objects.newInstance(InternalServices).loggingOutput
                    println("ADDING " + this + " TO " + loggingOutput)
                    loggingOutput.addOutputEventListener(this)
                }

                @Override
                synchronized void close() {
                    println("REMOVING " + this + " FROM " + loggingOutput)
                    loggingOutput.removeOutputEventListener(this)
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

                void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {}

                void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {}

                void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {}
            }

            File outputFile = file("${fixtureData.toURI()}")

            if (gradle.parent == null) {
                // Only register the service for the root build
                def services = gradle.services
                def outputProgress = gradle.sharedServices.registerIfAbsent("outputProgress", OutputProgressService) {
                    parameters.outputFile.set(outputFile)
                }

                // forces the service to be initialized immediately when configuration cache loads its cache
                gradle.services.get(BuildEventListenerRegistryInternal).onOperationCompletion(outputProgress)

                // forces the service to be initialized immediately
                outputProgress.get()
            }
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

    @Deprecated
    boolean downloadProgressLogged(URI url) {
        downloadProgressLogged(url.toASCIIString())
    }

    @Deprecated
    boolean downloadProgressLogged(String url) {
        return progressLogged("Download $url")
    }

    @Deprecated
    boolean uploadProgressLogged(URI url) {
        uploadProgressLogged(url.toString())
    }

    @Deprecated
    boolean uploadProgressLogged(String url) {
        return progressLogged("Upload $url")
    }

    @Deprecated
    boolean progressLogged(String operation) {
        println "-> CHECKING: $progressContent"
        def lines = progressContent
        def startIndex = lines.indexOf("[START " + operation + "]")
        if (startIndex == -1) {
            return false
        }
        lines = lines[startIndex..<lines.size()]
        lines = lines[0..lines.indexOf("[END " + operation + "]")]
        lines.size() >= 2
    }

    @Deprecated
    boolean statusLogged(String message) {
        return progressContent.contains("[" + message + "]")
    }

    @Deprecated
    boolean statusMatches(String regex) {
        return progressContent.any { it.matches("\\[" + regex + "\\]") }
    }
}
