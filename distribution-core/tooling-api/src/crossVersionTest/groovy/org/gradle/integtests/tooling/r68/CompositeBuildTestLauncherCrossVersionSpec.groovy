/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r68

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.TestLauncher
import spock.lang.Timeout

@Timeout(120)
@ToolingApiVersion(">=6.8")
@TargetGradleVersion('>=6.8')
class CompositeBuildTestLauncherCrossVersionSpec extends TestLauncherSpec {

    def setup() {
        file("included-build/src/test/java/example/IncludedTest.java").text = """
            package example;
            public class IncludedTest {
                @org.junit.Test
                public void foo() {
                     org.junit.Assert.assertEquals(1, 1);
                }
                @org.junit.Test
                public void foo2() {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """
        file("included-build/src/test/java/example/IncludedTest2.java").text = """
            package example;
            public class IncludedTest2 {
                @org.junit.Test
                public void bar() {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        settingsFile << "includeBuild('included-build')"
        file('included-build/settings.gradle') << "rootProject.name='included-build'"
        file('included-build/build.gradle') << """
        plugins {
            id("java")
        }
        ${mavenCentralRepository()}
        dependencies {
            testImplementation("junit:junit:4.13.1")
        }
        """
    }

    def "fails with meaningful error when no tests declared from included build"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':included-build:test', [])
        }

        then:
        def e = thrown(TestExecutionException)
        e.message == 'No test for task :included-build:test declared for execution.'

        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestMethods(':included-build:test', 'example.IncludedTest', [])
        }

        then:
        e = thrown(TestExecutionException)
        e.message == 'No test for task :included-build:test declared for execution.'
    }

    def "can target specific test task and classes from included build"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestClasses(':included-build:test', ["example.IncludedTest2"])
        }
        then:
        assertTaskExecuted(":included-build:test")
        assertTestExecuted(className: "example.IncludedTest2", methodName: "bar", task: ":included-build:test")
        assertTestNotExecuted(className: "example.IncludedTest")
    }

    def "can target specific test task and methods from included build"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTaskAndTestMethods(':included-build:test', "example.IncludedTest", ["foo2"])
        }
        then:
        assertTaskNotExecuted(":test")
        assertTaskExecuted(":included-build:test")
        assertTestExecuted(className: "example.IncludedTest", methodName: "foo2", task: ":included-build:test")
        assertTestNotExecuted(className: "example.IncludedTest", methodName: "foo")
    }
}
