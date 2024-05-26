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
        def executer = wrapperExecuter.withGradleUserHomeDir(gradleUserHome)
        result = executer.withTasks("hello").run()
        then:
        result.assertTaskExecuted(":hello")
        executer.stop()

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
        result = executer.withTasks("hello").run()
        then:
        deletedSomething
        result.assertHasErrorOutput("does not appear to contain a Gradle distribution.")
        result.assertTaskExecuted(":hello")
    }
}
