/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestResources
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.IgnoreIf

import static org.gradle.test.fixtures.junitplatform.JUnitPlatformTestRewriter.LATEST_JUPITER_VERSION

@IgnoreIf({ TestPrecondition.JDK7_OR_EARLIER })
class JUnitPlatformIgnoreClassIntegrationSpec extends AbstractIntegrationSpec {

    @Rule
    TestResources resources = new TestResources(temporaryFolder)

    def canHandleClassLevelIgnoredTests() {
        executer.noExtraLogging()
        buildFile << """
            dependencies { 
                testCompile 'org.junit.jupiter:junit-jupiter-api:${LATEST_JUPITER_VERSION}','org.junit.jupiter:junit-jupiter-engine:${LATEST_JUPITER_VERSION}'
            }

        """

        when:
        run('check')

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.IgnoredTest')
        result.testClass('org.gradle.IgnoredTest').assertTestCount(2, 0, 0).assertTestsSkipped("testIgnored1", "testIgnored2")
    }
}
