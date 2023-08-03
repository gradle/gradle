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
import org.gradle.integtests.fixtures.executer.AbstractGradleExecuter
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.embedded }) // wrapperExecuter requires a real distribution
class WrapperIntegrationTest extends AbstractWrapperIntegrationSpec {
    @IgnoreIf({ AbstractGradleExecuter.agentInstrumentationEnabled })
    // TODO(mlopatkin) fails because of race between daemon shutdown and deleting the file, as --no-daemon still spawns the single-use daemon
    def "can recover from a broken distribution"() {
        buildFile << "task hello"
        prepareWrapper()
        def gradleUserHome = testDirectory.file('some-custom-user-home')
        when:
        def executer = wrapperExecuter.withGradleUserHomeDir(null)
        // We can't use a daemon since on Windows the distribution jars will be kept open by the daemon
        executer.withArguments("-Dgradle.user.home=$gradleUserHome.absolutePath", "--no-daemon")
        result = executer.withTasks("hello").run()
        then:
        result.assertTaskExecuted(":hello")

        when:
        // Delete important file in distribution
        boolean deletedSomething = false
        gradleUserHome.eachFileRecurse(FileType.FILES) { file ->
            if (file.name.startsWith("gradle-launcher")) {
                deletedSomething |= file.delete()
                println("Deleting " + file)
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
