/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.java.compile.daemon

import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.logging.events.StyledTextOutputEvent

class JavaCompilerDaemonFailureIntegrationTest extends AbstractIntegrationSpec {
    def "startup failure messages from a compiler daemon are associated with the task that starts it"() {
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

        buildFile << """
            apply plugin: "java"

            tasks.compileJava {
                options.fork = true
                options.forkOptions.jvmArgs = ['--not-a-real-argument']
            }
        """

        file('src/main/java/ClassToCompile.java') << """
            class ClassToCompile {
            }
        """

        when:
        executer.withStackTraceChecksDisabled()
        fails("compileJava")

        then:
        failure.assertTasksExecuted(":compileJava")

        and:
        def compileJavaOperation = buildOperations.first(CompileJavaBuildOperationType)
        compileJavaOperation != null
        def outputProgress = compileJavaOperation.progress(StyledTextOutputEvent)
        // Output may come in different orders, so we just check that all the expected messages are there.
        def text = outputProgress*.details.spans*.text.flatten().join()
        text.count("Unrecognized option: --not-a-real-argument") == 1
        text.count("Error: Could not create the Java Virtual Machine.") == 1
        text.count("Error: A fatal exception has occurred. Program will exit.") == 1
        text.count("\n") == 3
    }
}
