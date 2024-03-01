/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.tooling.r82

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.TestSpecs
import spock.lang.Issue

@ToolingApiVersion('>=7.6')
@TargetGradleVersion(">=8.2")
class TestLauncherTestSpecCrossVersionSpec extends TestLauncherSpec {

    def setup() {
        withFailingTest() // ensures that withTestsFor statements are not ignored
    }

    @TargetGradleVersion(">=7.6 <8.2")
    @Issue("https://github.com/gradle/gradle/pull/25229")
    def "TestLauncher configuration ignored when configuration cache entry is reused in older Gradle versions"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withArguments("--configuration-cache")
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includePackage('example2')
            }
        }

        then:
        // 2 test class + 2 test method events
        events.testClassesAndMethods.size() == 4
        assertTestExecuted(className: 'example2.MyOtherTest', methodName: 'bar', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest2', methodName: 'baz', task: ':secondTest')

        when:
        events.clear()
        stdout.reset() // we are interested in the output of the second build only
        launchTests { TestLauncher launcher ->
            launcher.withArguments("--configuration-cache")
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includeClass("example.MyTest")
            }
        }

        then:
        // TestLauncher configuration ignored: only tests from the configuration cache entry is executed
        stdout.toString().contains("Reusing configuration cache.")
        events.testClassesAndMethods.size() == 4 // 2 test class + 2 test method events
        assertTestExecuted(className: 'example2.MyOtherTest', methodName: 'bar', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest2', methodName: 'baz', task: ':secondTest')
    }

    def "hits configuration cache with test filters changed"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withArguments("--configuration-cache")
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includePackage('example2')
            }
        }

        then:
        // 2 test class + 2 test method events
        events.testClassesAndMethods.size() == 4
        assertTestExecuted(className: 'example2.MyOtherTest', methodName: 'bar', task: ':secondTest')
        assertTestExecuted(className: 'example2.MyOtherTest2', methodName: 'baz', task: ':secondTest')

        when:
        events.clear()
        stdout.reset() // we are interested in the output of the second build only
        launchTests { TestLauncher launcher ->
            launcher.withArguments("--configuration-cache")
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includeClass("example.MyTest")
            }
        }

        then:
        // 1 test class + 2 test method events
        stdout.toString().contains("Reusing configuration cache.")
        events.testClassesAndMethods.size() == 3
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest')
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo2', task: ':secondTest')
    }

    @TargetGradleVersion(">=8.4")
    @Issue("https://github.com/gradle/gradle/issues/26056")
    def "Can execute same tests within different tests types with configuration cache enabled"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withArguments("--configuration-cache")
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':test').includeMethod('example.MyTest' , 'foo')
            }
        }

        then:
        events.testClassesAndMethods.size() == 2
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':test')

        when:
        events.clear()
        stdout.reset() // we are interested in the output of the second build only
        launchTests { TestLauncher launcher ->
            launcher.withArguments("--configuration-cache")
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':secondTest').includeMethod("example.MyTest", 'foo')
            }
        }

        then:
        !stdout.toString().contains("Reusing configuration cache.")
        events.testClassesAndMethods.size() == 2
        assertTestExecuted(className: 'example.MyTest', methodName: 'foo', task: ':secondTest')
    }
}
