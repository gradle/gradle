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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

class JULRedirectorIntegrationTest extends AbstractIntegrationSpec {
    def static final LYRICS = [
        "I'm a lumberjack, and I'm okay.",
        "I sleep all night and I work all day.",
        "He's a lumberjack, and He's okay.",
        "He sleeps all night and he works all day."
    ]
    @Rule TestResources testResources = new TestResources(temporaryFolder)

    /* Relies on the resources directory to differentiate from the other test(s) in this file. The
     * primary differences being that in this one's build.gradle file there is no setting of the
     * java.util.logging.config.file SystemProperty and the properties file itself does not exist.
     *
     * integTest/resources/org/gradle/api/internal/tasks/testing/defaultLoggingConfigNoFineLevel
     */
    def defaultLoggingConfigNoFineLevel() {
        when:
        run("test")

        then:
        DefaultTestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory);
        LYRICS.each {
            testResult.testClass("com.example.LumberJackTest").assertStderr(not(containsString(it)));
        }
    }

    /* Relies on the resources directory to differentiate from the other test(s) in this file. The
     * primary difference being that in this one's build.gradle file there is a setting of the
     * java.util.logging.config.file SystemProperty.
     *
     * integTest/resources/org/gradle/api/internal/tasks/testing/loggingConfigRespected
     */
    def loggingConfigRespected() {
       when:
        run("test")

        then:
        DefaultTestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory);
        LYRICS.each {
            testResult.testClass("com.example.LumberJackTest").assertStderr(containsString(it));
        }
    }
}
