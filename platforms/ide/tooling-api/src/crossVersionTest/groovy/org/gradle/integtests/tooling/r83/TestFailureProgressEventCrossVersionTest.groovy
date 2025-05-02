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

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TestFailureSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.Failure
import org.gradle.tooling.FileComparisonTestAssertionFailure
import org.gradle.tooling.internal.consumer.DefaultTestAssertionFailure

@ToolingApiVersion(">=8.3")
@TargetGradleVersion(">=8.3")
class TestFailureProgressEventCrossVersionTest extends TestFailureSpecification {

    def setup() {
        enableTestJvmDebugging = false
        enableStdoutProxying = false
    }

    def "Emits test failure events for org.opentest4j.MultipleFailuresError assertion errors in Junit 5"() {
        setupJUnit5()
        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;
            import org.opentest4j.MultipleFailuresError;
            import java.util.Arrays;

            public class JUnitJupiterTest {
                @Test
                void testingFileComparisonFailure() {
                    throw new MultipleFailuresError("Multiple errors detected", Arrays.asList(
                            new Exception("Exception 1"),
                            new Exception("Exception 2"),
                            new Exception("Exception 3")
                    ));
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 1
        collector.failures[0] instanceof DefaultTestAssertionFailure

        DefaultTestAssertionFailure failure = collector.failures[0] as DefaultTestAssertionFailure
        failure.causes.size() == 3
        failure.causes.eachWithIndex { Failure entry, int i ->
            assert entry.message == "Exception ${i + 1}"
        }
    }

    def "Emits test failure events for org.opentest4j.MultipleFailuresError assertion errors in Junit 4"() {
        setupJUnit4()
        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.Test;
            import org.opentest4j.MultipleFailuresError;
            import java.util.Arrays;

            public class JUnitJupiterTest {
                @Test
                public void testingFileComparisonFailure() {
                    throw new MultipleFailuresError("Multiple errors detected", Arrays.asList(
                            new Exception("Exception 1"),
                            new Exception("Exception 2"),
                            new Exception("Exception 3")
                    ));
                }
            }
        '''
        def collector = new TestFailureEventCollector()

        when:
        runTestTaskWithFailureCollection(collector)

        then:
        thrown(BuildException)
        collector.failures.size() == 1
        collector.failures[0] instanceof DefaultTestAssertionFailure

        DefaultTestAssertionFailure failure = collector.failures[0] as DefaultTestAssertionFailure
        failure.causes.size() == 3
        failure.causes.eachWithIndex { Failure entry, int i ->
            assert entry.message == "Exception ${i + 1}"
        }
    }

    def "Emits test failure events for org.opentest4j.AssertionFailedError assertion errors in Junit 5"() {
        setupJUnit5()
        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;
            import org.opentest4j.AssertionFailedError;
            import org.opentest4j.FileInfo;

            public class JUnitJupiterTest {
                 @Test
                 void testingFileComparisonFailure() {
                    FileInfo from = new FileInfo("/path/from", new byte[]{ 0x0 });
                    FileInfo to = new FileInfo("/path/to", new byte[]{ 0x1 });
                    throw new AssertionFailedError("Different files detected",  from, to);
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

        FileComparisonTestAssertionFailure failure = collector.failures[0] as FileComparisonTestAssertionFailure
        failure.expected == '/path/from'
        failure.actual == '/path/to'
        failure.expectedContent == new byte[]{0x0}
        failure.actualContent == new byte[]{0x1}
    }

    def "Emits test failure events for org.opentest4j.AssertionFailedError assertion errors in Junit 4"() {
        setupJUnit4()
        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.Test;
            import org.opentest4j.AssertionFailedError;
            import org.opentest4j.FileInfo;

            public class JUnitJupiterTest {
                 @Test
                 public void testingFileComparisonFailure() {
                    FileInfo from = new FileInfo("/path/from", new byte[]{ 0x0 });
                    FileInfo to = new FileInfo("/path/to", new byte[]{ 0x1 });
                    throw new AssertionFailedError("Different files detected",  from, to);
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

        FileComparisonTestAssertionFailure failure = collector.failures[0] as FileComparisonTestAssertionFailure
        failure.expected == '/path/from'
        failure.actual == '/path/to'
        failure.expectedContent == new byte[]{0x0}
        failure.actualContent == new byte[]{0x1}
    }
}
