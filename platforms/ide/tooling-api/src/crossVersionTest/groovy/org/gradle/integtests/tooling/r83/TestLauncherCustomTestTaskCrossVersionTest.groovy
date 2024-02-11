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

package org.gradle.integtests.tooling.r83

import org.gradle.integtests.tooling.TestLauncherSpec
import org.gradle.integtests.tooling.fixture.Snippets
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.TestExecutionException
import org.gradle.tooling.TestLauncher
import org.gradle.tooling.TestSpecs
import spock.lang.Issue

@ToolingApiVersion('>=7.6')
@TargetGradleVersion(">=8.3")
class TestLauncherCustomTestTaskCrossVersionTest extends TestLauncherSpec {

    def setup() {
        Snippets.customTestTask(buildFile, file('buildSrc'))
    }

    @Issue('https://github.com/gradle/gradle/issues/25370')
    @TargetGradleVersion(">=7.6 <8.3")
    def "Cannot run tests with custom task implementation in older Gradle versions"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':myTestTask').includeClass('org.my.MyClass')
            }
        }

        then:
        Throwable exception = thrown(TestExecutionException)
        exception.cause.message == "Task ':myTestTask' of type 'CustomTestTask_Decorated' not supported for executing tests via TestLauncher API."
    }

    @Issue('https://github.com/gradle/gradle/issues/25370')
    def "Can run tests with custom task implementation"() {
        when:
        launchTests { TestLauncher launcher ->
            launcher.withTestsFor { TestSpecs specs ->
                specs.forTaskPath(':myTestTask').includeClass('org.my.MyClass')
            }
        }

        then:
        events.testClassesAndMethods.size() == 1
        assertTestExecuted(className: 'org.my.MyClass', methodName: 'MyCustomTest', task: ':myTestTask')
    }
}
