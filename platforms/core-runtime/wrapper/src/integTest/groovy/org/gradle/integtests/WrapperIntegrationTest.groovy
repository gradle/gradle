/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.integtests

import groovy.io.FileType
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import java.nio.file.Files

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperIntegrationTest extends AbstractWrapperIntegrationSpec {
    def "can recover from a broken distribution"() {
        buildFile << "task hello"
        prepareWrapper()
        def gradleUserHome = testDirectory.file('some-custom-user-home')
        when:
        def executer = wrapperExecuter.withGradleUserHomeDir(null)
        // We can't use a daemon since on Windows the distribution jars will be kept open by the daemon
        executer.withArguments(
            "-Dgradle.user.home=$gradleUserHome.absolutePath",
            // TODO(https://github.com/gradle/gradle/issues/24057) having agent enabled forces the single-use daemon to spawn,
            //  because the wrapper process has no agent applied. Even the short-lived daemon causes a race between its shutdown and
            //  the deletion code below.
            "-D${DaemonBuildOptions.ApplyInstrumentationAgentOption.GRADLE_PROPERTY}=false",
            "--no-daemon")
        result = executer.withTasks("hello").run()
        then:
        result.assertTaskExecuted(":hello")

        when:
        // Delete important file in distribution
        boolean deletedSomething = false
        gradleUserHome.eachFileRecurse(FileType.FILES) { file ->
            if (file.name.startsWith("gradle-launcher")) {
                Files.delete(file.toPath())
                println("Deleting " + file)
                deletedSomething = true
            }
        }
        and:
        executer.withArguments("-Dgradle.user.home=$gradleUserHome.absolutePath", "--no-daemon")
        result = executer.withTasks("hello").run()
        then:
        deletedSomething
        result.assertHasErrorOutput("does not appear to contain a Gradle distribution.")
        result.assertTaskExecuted(":hello")
    }
}
