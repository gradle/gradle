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
import org.gradle.tooling.BuildException
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.TestLauncher
import spock.lang.Issue
import spock.lang.Timeout

import static org.gradle.integtests.tooling.fixture.TextUtil.normaliseLineSeparators

@TargetGradleVersion(">=3.0")
@Timeout(120)
class TestLauncherCrossVersionSpec extends TestLauncherSpec {

    def "can execute test methods of JVM test class"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestMethods("example.MyTest", "foo")
        }
        then:

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        events.tests.size() == (supportsEfficientClassFiltering() ? 8 : 12)

        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestNotExecuted(className: "example2.MyOtherTest2", methodName: "baz", task: ":test")
        assertTestNotExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
        assertTestNotExecuted(className: "example2.MyOtherTest2", methodName: "baz", task: ":secondTest")

        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestMethods("example.MyTest", "foo", "foo2")
            launcher.withJvmTestMethods("example2.MyOtherTest", "bar")
        }
        then:

        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")

        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")

        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":test")
        assertTestExecuted(className: "example2.MyOtherTest", methodName: "bar", task: ":secondTest")
        events.tests.size() == (supportsEfficientClassFiltering() ? 14 : 16)

        assertTestNotExecuted(className: "example2.MyOtherTest2", methodName: "baz", task: ":test")
        assertTestNotExecuted(className: "example2.MyOtherTest2", methodName: "baz", task: ":secondTest")
    }

    def "executes all test methods if class and method is declared"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("example.MyTest")
            launcher.withJvmTestMethods("example.MyTest", "foo")
        }

        then:
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":test")
        assertTestExecuted(className: "example.MyTest", methodName: "foo", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":test")
        events.tests.size() == (supportsEfficientClassFiltering() ? 10 : 14)
    }

    def "fails with meaningful error when requested tests not found"() {
        given:
        collectDescriptorsFromBuild()
        and:
        testClassRemoved()
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestClasses("org.acme.NotExistingTestClass")
            launcher.withJvmTestMethods("example.MyTest", "unknownMethod")
            launcher.withJvmTestMethods("example.MyTest", "unknownMethod2")
            launcher.withJvmTestMethods("example.UnknownClass", "unknownTestMethod3")
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
        Test method example.MyTest.unknownMethod()
        Test method example.MyTest.unknownMethod2()
        Test method example.UnknownClass.unknownTestMethod3()"""
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
        when:
        launchTests { TestLauncher launcher ->
            launcher.withJvmTestMethods("util.TestUtil", "someUtilMethod")
        }
        then:
        e = thrown(TestExecutionException)
        normaliseLineSeparators(e.cause.message) == """No matching tests found in any candidate test task.
    Requested tests:
        Test method util.TestUtil.someUtilMethod()"""
    }

    @TargetGradleVersion(">=3.0 <8.4")
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
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail2", task: ":test")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":secondTest")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail2", task: ":secondTest")
        def e = thrown(TestExecutionException)
        normaliseLineSeparators(e.cause.message) == """Test failed.
    Failed tests:
        Test example.MyFailingTest#fail (Task: :secondTest)
        Test example.MyFailingTest#fail2 (Task: :secondTest)
        Test example.MyFailingTest#fail (Task: :test)
        Test example.MyFailingTest#fail2 (Task: :test)"""

        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withJvmTestMethods("example.MyFailingTest", "fail")
        }

        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":test")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyFailingTest", methodName: "fail2", task: ":test")
        assertTestNotExecuted(className: "example.MyFailingTest", methodName: "fail2", task: ":secondTest")
        e = thrown(TestExecutionException)
        normaliseLineSeparators(e.cause.message) == """Test failed.
    Failed tests:
        Test example.MyFailingTest#fail (Task: :secondTest)
        Test example.MyFailingTest#fail (Task: :test)"""
    }

    @Issue("https://github.com/gradle/gradle/issues/26195")
    @TargetGradleVersion(">=8.4")
    def "throws exception with meaningful error message on failing tests with Gradle 8.4+"() {
        setup:
        withFailingTest()

        when:
        launchTests { TestLauncher testLauncher ->
            // --continue set to run both test tasks
            testLauncher.withJvmTestClasses("example.MyFailingTest").withArguments("--continue")
        }

        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":test")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail2", task: ":test")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":secondTest")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail2", task: ":secondTest")
        // because of --continue we get a different exception hierarchy
        def e = thrown(BuildException)
        normaliseLineSeparators(e.cause.causes[0].cause.message) == "Execution failed for task ':secondTest'."
        normaliseLineSeparators(e.cause.causes[0].cause.cause.message).startsWith "There were failing tests. See the report at:"
        normaliseLineSeparators(e.cause.causes[1].cause.message) == "Execution failed for task ':test'."
        normaliseLineSeparators(e.cause.causes[1].cause.cause.message).startsWith "There were failing tests. See the report at:"

        when:
        launchTests { TestLauncher testLauncher ->
            testLauncher.withJvmTestMethods("example.MyFailingTest", "fail").addArguments("--continue")
        }

        then:
        assertTaskExecuted(":test")
        assertTaskExecuted(":secondTest")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":test")
        assertTestExecuted(className: "example.MyFailingTest", methodName: "fail", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyFailingTest", methodName: "fail2", task: ":test")
        assertTestNotExecuted(className: "example.MyFailingTest", methodName: "fail2", task: ":secondTest")
        e = thrown(BuildException)
        normaliseLineSeparators(e.cause.causes[0].cause.message) == "Execution failed for task ':secondTest'."
        normaliseLineSeparators(e.cause.causes[0].cause.cause.message).startsWith "There were failing tests. See the report at:"
        normaliseLineSeparators(e.cause.causes[1].cause.message) == "Execution failed for task ':test'."
        normaliseLineSeparators(e.cause.causes[1].cause.cause.message).startsWith "There were failing tests. See the report at:"
    }

    def testClassRemoved() {
        file("src/test/java/example2/MyOtherTest.java").delete()
    }
}
