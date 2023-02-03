/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.tooling.r61

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.TestLauncher
import spock.lang.Timeout

@Timeout(120)
@ToolingApiVersion('>=6.1')
@TargetGradleVersion(">=6.1")
class TestLauncherCrossVersionSpec extends TestLauncherSpec {

    def setup() {
        file("src/test/java/example/MyTest2.java").text = """
            package example;
            public class MyTest2 {
                @org.junit.Test public void bar() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """
    }

    def "fails with meaningful error when no tests declared"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':test',[])
        }

        then:
        def e = thrown(TestExecutionException)
        e.message == 'No test for task :test declared for execution.'

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestMethods(':test', 'example.MyTest2', [])
        }

        then:
        e = thrown(TestExecutionException)
        e.message == 'No test for task :test declared for execution.'
    }


    @TargetGradleVersion(">2.6 <6.1")
    def "no tests executed when withTaskAndTestClasses() invoked for old clients"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':secondTest',["example.MyTest"])
        }

        then:
        def e = thrown(Exception)
        e.cause.message.contains 'No matching tests found'
        assertTaskNotExecuted(":test")
        assertTaskNotExecuted(":secondTest")
    }

    def "can target specific test task and classes"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':secondTest',["example.MyTest2"])
        }
        then:
        assertTaskNotExecuted(":test")
        assertTaskExecuted(":secondTest")
        assertTestExecuted(className: "example.MyTest2", methodName: "bar", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyTest")
    }

    def "can target specific test task and methods"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestMethods(':secondTest', "example.MyTest", ["foo2"])
        }
        then:
        assertTaskNotExecuted(":test")
        assertTaskExecuted(":secondTest")
        assertTestExecuted(className: "example.MyTest", methodName: "foo2", task: ":secondTest")
        assertTestNotExecuted(className: "example.MyTest", methodName: "foo")
    }
}
