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

package org.gradle.sample

import org.gradle.testkit.functional.BuildResult
import org.gradle.testkit.functional.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

// START SNIPPET functional-test-spock
class UserFunctionalTest extends Specification {
    @Rule final TemporaryFolder testProjectDir = new org.junit.rules.TemporaryFolder()
    File buildFile

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
    }

    def "execute task 'helloWorld'"() {
        given: "write build script file under test"
        buildFile << """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        """

        when: "create, configure and execute Gradle runner"
        GradleRunner gradleRunner = GradleRunner.create()
        gradleRunner.withWorkingDir(testProjectDir.root).withTasks('helloWorld')
        BuildResult result = gradleRunner.succeeds()

        then: "verify build result"
        result.standardOutput.contains('Hello world!')
        result.standardError == ''
        result.executedTasks == [':helloWorld']
        result.skippedTasks.empty
    }
}
// END SNIPPET functional-test-spock
