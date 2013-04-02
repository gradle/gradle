/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.TestResources
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@TargetVersions(['4.5', '4.11'])
public class JUnitAssumptionsIntegrationTest extends MultiVersionIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    @Before
    public void before() {
        executer.noExtraLogging()
    }

    private void configureJUnit() {
        buildFile << """
        dependencies {
            testCompile 'junit:junit:$version'
        }"""
    }

    @Test
    public void supportsAssumptions() {
        setup:
        configureJUnit()
        when:
        executer.withTasks('check').run()
        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.TestWithAssumptions')
        result.testClass('org.gradle.TestWithAssumptions')
                .assertTestCount(2, 0, 0)
                .assertTestsExecuted('assumptionSucceeded')
                .assertTestPassed('assumptionSucceeded')
                .assertTestsSkipped('assumptionFailed')
    }
}
