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
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.TestLauncher

import static org.gradle.integtests.tooling.fixture.TextUtil.normaliseLineSeparators

@ToolingApiVersion(">=2.7")
@TargetGradleVersion(">=2.7")
class TestLauncherCrossVersionSpec extends TestLauncherSpec {

    def "can specify test by class and method name"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestMethods("example.MyTest", "foo")
        }
        then:

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")

        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
    }

    def "fails with meaningful error when requested tests not found"() {
        given:
        collectDescriptorsFromBuild()
        and:
        testClassRemoved()
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestMethods("example.MyTest", "unknownMethod")
            launcher.withJvmTestMethods("example.MyTest", "unknownMethod2")
            launcher.withJvmTestClasses("org.acme.NotExistingTestClass")
            launcher.withTests(testDescriptors("example2.MyOtherTest", null, ":test"))
        }
        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")
        def e = thrown(TestExecutionException)
        normaliseLineSeparators(e.cause.message) == """No matching tests found in any candidate test task.
    Requested tests:
        Test class example2.MyOtherTest (Task: ':test')
        Test class org.acme.NotExistingTestClass
        Test method example.MyTest#unknownMethod
        Test method example.MyTest#unknownMethod2"""
    }

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
        normaliseLineSeparators(e.cause.message) == """No matching tests found in any candidate test task.
    Requested tests:
        Test class util.TestUtil"""
    }

    def "throws exception with meaningful error message on failing tests"() {
        setup:
        withFailingTest()
        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withJvmTestClasses("example.MyFailingTest")
        }

        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":test")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":secondTest")
        def e = thrown(TestExecutionException)
        normaliseLineSeparators(e.cause.message) == """Test failed.
    Failed tests:
        Test example.MyFailingTest#fail (Task: :secondTest)
        Test example.MyFailingTest#fail (Task: :test)"""
    }

    def testClassRemoved() {
        file("src/test/java/example2/MyOtherTest.java").delete()
    }
}
