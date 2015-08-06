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

package org.gradle.integtests.tooling.r27

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.TestLauncher

@ToolingApiVersion(">=2.7")
@TargetGradleVersion(">=2.7")
class TestLauncherCrossVersionSpec extends TestLauncherSpec {

    def "fails with meaningful error when declared class has no tests"() {
        given:
        file("src/test/java/util/TestUtil.java") << """
            package util;
            public class TestUtil {
                static void someUtilsMethod(){}
            }
        """
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("util.TestUtil")
        }
        then:
        def e = thrown(TestExecutionException)
        org.gradle.util.TextUtil.normaliseLineSeparators(e.cause.message) == """No matching tests found in any candidate test task.
    Requested Tests:
        Test class util.TestUtil"""
    }

    def "fails with meaningful error when test no longer exists"() {
        given:
        collectDescriptorsFromBuild()
        and:
        testClassRemoved()
        when:
        launchTests(testDescriptors("example.MyTest", null, ":test"));
        then:
        assertTaskExecuted(":test")
        assertTaskNotExecuted(":secondTest")

        def e = thrown(TestExecutionException)
        org.gradle.util.TextUtil.normaliseLineSeparators(e.cause.message) == """No matching tests found in any candidate test task.
    Requested Tests:
        Test class example.MyTest (Task: ':test')"""
    }

    def "fails with meaningful error when test class not available for any test task"() {
        when:
        withConnection { ProjectConnection connection ->
            def testLauncher = connection.newTestLauncher()
            testLauncher.withJvmTestClasses("org.acme.NotExistingTestClass")
            testLauncher.run()
        };
        then:
        assertTaskNotExecuted(":test")
        assertTaskNotExecuted(":secondTest")

        def e = thrown(TestExecutionException)
        org.gradle.util.TextUtil.normaliseLineSeparators(e.cause.message) == """No matching tests found in any candidate test task.
    Requested Tests:
        Test class org.acme.NotExistingTestClass"""
    }


}
