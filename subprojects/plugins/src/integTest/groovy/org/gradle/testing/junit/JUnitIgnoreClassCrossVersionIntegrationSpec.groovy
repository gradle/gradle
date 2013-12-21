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


@TargetVersions(['4.4', '4.8.2', '4.11'])
class JUnitIgnoreClassCrossVersionIntegrationSpec extends MultiVersionIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)

    String junitDependency = "junit:junit:$version"

    @Before
    public void before() {
        executer.noExtraLogging()
        configureJUnit()
    }

    private void configureJUnit() {
        buildFile << """
        dependencies {
        testCompile '${junitDependency.toString()}'
        }"""
    }

    @Test
    public void canHandleClassLevelIgnoredTests() {
        resources.maybeCopy('JUnitIntegrationTest/ignoreTests')
        executer.withTasks('check').run()
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.IgnoredTest', 'org.gradle.CustomIgnoredTest')
        result.testClass('org.gradle.IgnoredTest').assertTestCount(1, 0, 0).assertTestsSkipped("testIgnored")
        result.testClass('org.gradle.CustomIgnoredTest').assertTestCount(3, 0, 0).assertTestsSkipped("first test run", "second test run", "third test run")
    }
}
