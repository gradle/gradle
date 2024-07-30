/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.r810

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestFailureSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.FileComparisonTestAssertionFailure
import spock.lang.Issue

@ToolingApiVersion(">=8.3")
@TargetGradleVersion(">=8.10")
class TestFailureProgressEventCrossVersionTest extends TestFailureSpecification {

    def setup() {
        enableTestJvmDebugging = false
        enableStdoutProxying = true
    }

    @Issue("https://github.com/gradle/gradle/issues/29994")
    def "Custom assertion file info are emitted as test failure events using JUnit 4"() {
        given:
        setupJUnit4()
        file('src/test/java/org/gradle/JUnitTest.java') << '''
            package org.gradle;

            import org.junit.Test;
            import org.opentest4j.AssertionFailedError;
            import org.opentest4j.FileInfo;

            public class JUnitTest {
                @Test
                public void test() {
                    CustomFileInfo expected = new CustomFileInfo("/path/from", new byte[]{ 0x0 });
                    throw new AssertionFailedError(
                        "Asymmetric expected and actual objects",
                        expected,
                        "actual content"
                    );
                }

                private static class CustomFileInfo extends FileInfo {
                    CustomFileInfo(String path, byte[] contents) {
                        super(path, contents);
                    }
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 1
        collector.failures[0] instanceof FileComparisonTestAssertionFailure

        def failure = collector.failures[0] as FileComparisonTestAssertionFailure
        failure.expected == '/path/from'
        failure.expectedContent == new byte[]{0x0}
        failure.actual == 'actual content'
    }


    @Issue("https://github.com/gradle/gradle/issues/29994")
    def "Custom assertion file info are emitted as test failure events using JUnit 5"() {
        given:
        setupJUnit5()
        file('src/test/java/org/gradle/JUnitTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;
            import org.opentest4j.AssertionFailedError;
            import org.opentest4j.FileInfo;

            public class JUnitTest {
                @Test
                public void test() {
                    CustomFileInfo expected = new CustomFileInfo("/path/from", new byte[]{ 0x0 });
                    throw new AssertionFailedError(
                        "Asymmetric expected and actual objects",
                        expected,
                        "actual content"
                    );
                }

                private static class CustomFileInfo extends FileInfo {
                    CustomFileInfo(String path, byte[] contents) {
                        super(path, contents);
                    }
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 1
        collector.failures[0] instanceof FileComparisonTestAssertionFailure

        def failure = collector.failures[0] as FileComparisonTestAssertionFailure
        failure.expected == '/path/from'
        failure.expectedContent == new byte[]{0x0}
        failure.actual == 'actual content'
    }
}
