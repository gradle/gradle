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

package org.gradle.api.internal.tasks.testing

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.not

class JULRedirectorIntegrationTest extends AbstractSampleIntegrationTest {
    def static final LYRICS = [
        "I'm a lumberjack, and I'm okay.",
        "I sleep all night and I work all day.",
        "He's a lumberjack, and He's okay.",
        "He sleeps all night and he works all day."
    ]
    @Rule TestResources testResources = new TestResources(temporaryFolder)

    /* Relies on the resources directory:
     * integTest/resources/org/gradle/api/internal/tasks/testing/loggingConfig
     */
    def defaultLoggingConfigNoFineLevel() {
        given:
        testResources.maybeCopy('JULRedirectorIntegrationTest/loggingConfig')

        when:
        run("test")

        then:
        DefaultTestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory);
        LYRICS.each {
            testResult.testClass("com.example.LumberJackTest").assertStderr(not(containsString(it)));
        }
    }

    /* Relies on the resources directory:
     * integTest/resources/org/gradle/api/internal/tasks/testing/loggingConfig
     */
    def loggingConfigRespected() {
        given:
        testResources.maybeCopy('JULRedirectorIntegrationTest/loggingConfig')
        buildFile << """
            test {
                systemProperty 'java.util.logging.config.file', 'src/test/resources/logging.properties'
            }
        """

        when:
        run("test")

        then:
        DefaultTestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory);
        LYRICS.each {
            testResult.testClass("com.example.LumberJackTest").assertStderr(containsString(it));
        }
    }
}
