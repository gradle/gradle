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

package org.gradle.testing.junit.junit4

import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.util.internal.VersionNumber
import spock.lang.Issue

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4

@TargetCoverage({ JUNIT_4 })
class JUnit4TestClassDetectionIntegrationTest extends AbstractJUnit4TestClassDetectionIntegrationTest implements JUnit4MultiVersionTest {
    @Override
    boolean isSupportsEmptyClassWithRunner() {
        // JUnit 4.0 does not support an empty class with a runner (i.e. the test fails)
        return VersionNumber.parse(version) >= VersionNumber.parse('4.1')
    }

    @Issue("https://github.com/gradle/gradle/issues/36508")
    def "scanForTestClasses=false bypasses framework detection so include patterns alone determine which classes execute"() {
        given:
        // The parent class with @RunWith lives in a separate source set whose output is packaged
        // into an archive with a non-.jar extension. javac and the JVM accept it as a classpath
        // entry, but AbstractTestFrameworkDetector only inspects directories and *.jar files,
        // so the parent class is invisible to the detector. This mirrors the user's scenario
        // where the parent class is on the JPMS module path (also invisible to the detector).
        file('src/parentLib/java/parent/CustomRunner.java') << """
            package parent;

            import org.junit.runner.*;
            import org.junit.runner.notification.RunNotifier;

            public class CustomRunner extends Runner {
                private final Description description;

                public CustomRunner(Class type) throws Exception {
                    description = Description.createSuiteDescription(type);
                    description.addChild(Description.createTestDescription(type, "ok"));
                }

                @Override
                public Description getDescription() {
                    return description;
                }

                @Override
                public void run(RunNotifier notifier) {
                    for (Description child : description.getChildren()) {
                        notifier.fireTestStarted(child);
                        notifier.fireTestFinished(child);
                    }
                }
            }
        """.stripIndent()
        file('src/parentLib/java/parent/AbstractRunWith.java') << """
            package parent;

            import org.junit.runner.RunWith;

            @RunWith(CustomRunner.class)
            public abstract class AbstractRunWith {
            }
        """.stripIndent()
        file('src/test/java/example/SubTest.java') << """
            package example;

            public class SubTest extends parent.AbstractRunWith {
            }
        """.stripIndent()

        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            sourceSets {
                parentLib {
                    java.srcDir 'src/parentLib/java'
                }
            }
            dependencies {
                ${getTestFrameworkDependencies('parentLib')}
                ${testFrameworkDependencies}
            }
            tasks.register('parentLibArchive', Jar) {
                from sourceSets.parentLib.output
                archiveExtension = 'zip'
            }
            dependencies {
                testImplementation files(tasks.parentLibArchive)
            }
            test.${configureTestFramework}
        """.stripIndent()

        when:
        // With the default scanForTestClasses=true, AbstractTestFrameworkDetector visits SubTest.class,
        // sees no @Test/@RunWith on the class itself, attempts to scan the superclass
        // parent/AbstractRunWith.class, fails to locate it (the .zip archive is silently skipped),
        // and silently drops SubTest. failOnNoDiscoveredTests then fires.
        fails 'test'

        then:
        failure.assertHasCause("There are test sources present and no filters are applied, but the test task did not discover any tests to execute. This is likely due to a misconfiguration. Please check your test configuration. If this is not a misconfiguration, this error can be disabled by setting the 'failOnNoDiscoveredTests' property to false.")

        when:
        buildFile << """
            test {
                scanForTestClasses = false
                include '**/SubTest.class'
            }
        """.stripIndent()
        succeeds 'test'

        then:
        def results = resultsFor(testDirectory)
        results.testPath('example.SubTest', 'ok').onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
    }
}
