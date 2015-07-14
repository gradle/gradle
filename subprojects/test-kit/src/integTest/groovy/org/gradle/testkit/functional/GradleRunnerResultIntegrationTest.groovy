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

package org.gradle.testkit.functional

/**
 * Tests more intricate aspects of the BuildResult object
 */
class GradleRunnerResultIntegrationTest extends AbstractGradleRunnerIntegrationTest {

    def "execute task actions marked as up-to-date or skipped"() {
        given:
        buildFile << """
            task helloWorld

            task byeWorld {
                onlyIf {
                    false
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld', 'byeWorld')
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld UP-TO-DATE')
        result.standardOutput.contains(':byeWorld SKIPPED')
        result.executedTasks == [':helloWorld', ':byeWorld']
        result.skippedTasks == [':helloWorld', ':byeWorld']
    }

}
