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

package org.gradle.nativeplatform.test.xctest

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement

abstract class AbstractSwiftXCTestWithComponentIntegrationTest extends AbstractSwiftXCTestIntegrationTest {

    @ToBeFixedForConfigurationCache
    def "can test public and internal features"() {
        given:
        def fixture = getPassingTestFixtureUsingPublicAndInternalFeatures()
        makeSingleProject()
        settingsFile << "rootProject.name = '${fixture.projectName}'"
        fixture.writeToProject(testDirectory)

        when:
        succeeds("test")

        then:
        result.assertTasksExecuted(tasksToCompileComponentUnderTest, tasksToBuildAndRunUnitTest, ":test")
        fixture.assertTestCasesRan(testExecutionResult)
    }

    protected abstract XCTestSourceElement getPassingTestFixtureUsingPublicAndInternalFeatures()
}
