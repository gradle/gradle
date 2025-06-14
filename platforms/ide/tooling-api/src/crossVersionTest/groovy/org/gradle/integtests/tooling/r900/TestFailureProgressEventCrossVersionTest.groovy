/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling.r900

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestFailureSpecification
import org.gradle.tooling.BuildException
import org.gradle.tooling.internal.consumer.DefaultTestFrameworkFailure

@TargetGradleVersion(">=9.0.0")
class TestFailureProgressEventCrossVersionTest extends TestFailureSpecification {
    def "Emits test failure events when bad jvm arg stops worker from starting"() {
        given:
        buildFile << """
            dependencies {
                testCompileOnly("org.junit.jupiter:junit-jupiter:5.12.2")
            }

            test {
                jvmArgs('-phasers=stun')
            }
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.jupiter.api.Test
                void test() {}
            }
        """

        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 1
        collector.failures[0] instanceof DefaultTestFrameworkFailure

        DefaultTestFrameworkFailure failure = collector.failures[0] as DefaultTestFrameworkFailure
        failure.message =~ /Process 'Gradle Test Executor \d+' finished with non-zero exit value 1/
    }

    @TargetGradleVersion(">=9.0.0")
    def "Emits test failure events missing junit 4 framework for testing"() {
        given:
        buildFile << """
            dependencies {
                testCompileOnly("junit:junit:4.13.2")
            }

            test.useJUnit()
        """

        file("src/test/java/MyTest.java") << """
            public class MyTest {
                @org.junit.Test
                public void test() {}
            }
        """

        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 2
        collector.failures[0] instanceof DefaultTestFrameworkFailure

        DefaultTestFrameworkFailure failure1 = collector.failures[0] as DefaultTestFrameworkFailure
        failure1.className == "org.gradle.api.internal.tasks.testing.TestSuiteExecutionException"
        failure1.message =~ /Could not start Gradle Test Executor \d+: Failed to load JUnit 4.  Please ensure that JUnit 4 is available on the test runtime classpath./

        DefaultTestFrameworkFailure failure2 = collector.failures[1] as DefaultTestFrameworkFailure
        failure2.className == "org.gradle.api.internal.tasks.testing.TestFrameworkStartupFailureException"
        failure2.message =~ /Could not start Gradle Test Executor \d+: Failed to load JUnit 4.  Please ensure that JUnit 4 is available on the test runtime classpath./
        failure1.causes.size() == 1
    }
}
