/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing.testng

import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.TestNGCoverage

import static org.gradle.testing.fixture.TestNGCoverage.FIXED_ICLASS_LISTENER
import static org.gradle.testing.fixture.TestNGCoverage.NEWEST
import static org.gradle.util.TextUtil.normaliseFileSeparators

@TargetCoverage({ [FIXED_ICLASS_LISTENER, NEWEST] })
class TestNGClassIntegrationTest extends MultiVersionIntegrationSpec {

    private final static String STARTED = 'Started'
    private final static String FINISHED = 'Finished'

    def setup() {
        executer.noExtraLogging()
        TestNGCoverage.enableTestNG(buildFile, version)

        buildFile << """
            import org.gradle.api.internal.tasks.testing.TestCompleteEvent
            import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
            import org.gradle.api.internal.tasks.testing.TestStartEvent
            import org.gradle.api.internal.tasks.testing.results.TestListenerInternal
            import org.gradle.api.tasks.testing.TestOutputEvent
            import org.gradle.api.tasks.testing.TestResult
    
            test {
                useTestNG()
            }
    
            gradle.addListener(new TestListenerInternal() {
                void started(TestDescriptorInternal d, TestStartEvent s) {
                    printEventInformation('$STARTED', d)
                }
    
                void completed(TestDescriptorInternal d, TestResult result, TestCompleteEvent cE) {
                    printEventInformation('$FINISHED', d)
                }
    
                private void printEventInformation(String eventTypeDescription, TestDescriptorInternal d) {
                    def name = d.name
                    def descriptor = d;
                    while (descriptor.parent != null) {
                        name = "\${descriptor.parent.name} > \$name"
                        descriptor = descriptor.parent
                    }
                    println "\$eventTypeDescription event type \${d.descriptor.class.name} for \${name}"
                }
    
                void output(TestDescriptorInternal descriptor, TestOutputEvent output) {
                }
            })
        """
    }

    @ToBeFixedForInstantExecution
    def "test class events references correct suite as parent"() {
        given:
        def testNgSuite = file("src/test/resources/testng.xml")

        buildFile << """
            test {
                useTestNG {
                    suites file("${(normaliseFileSeparators(testNgSuite.absolutePath))}")
                }
            }
        """

        file("src/test/java/org/company/SystemOutTest.java") << """
            package org.company;
    
            import org.testng.Assert;
            import org.testng.annotations.Test;
    
            public class SystemOutTest {
                @Test
                public void testOut() {
                    System.out.println("System.out rules!");
                    Assert.assertTrue(true);
                }
            }
        """

        testNgSuite << """
            <!DOCTYPE suite SYSTEM "http://testng.org/testng-1.0.dtd">
            <suite name="TestSuite">
                <test name="LightTest">
                    <classes>
                        <class name="org.company.SystemOutTest"/>
                    </classes>
                </test>
                <test name="FullTest">
                    <classes>
                        <class name="org.company.SystemOutTest"/>
                    </classes>
                </test>
            </suite>
        """

        when:
        succeeds 'test'

        then:
        containsEvent(STARTED, DefaultTestSuiteDescriptor, 'TestSuite > LightTest')
        containsEvent(STARTED, DefaultTestClassDescriptor, 'TestSuite > LightTest > org.company.SystemOutTest')
        containsEvent(STARTED, DefaultTestMethodDescriptor, 'TestSuite > LightTest > org.company.SystemOutTest > testOut')
        containsEvent(FINISHED, DefaultTestMethodDescriptor, 'TestSuite > LightTest > org.company.SystemOutTest > testOut')
        containsEvent(FINISHED, DefaultTestClassDescriptor, 'TestSuite > LightTest > org.company.SystemOutTest')
        containsEvent(FINISHED, DefaultTestSuiteDescriptor, 'TestSuite > LightTest')
        containsEvent(STARTED, DefaultTestSuiteDescriptor, 'TestSuite > FullTest')
        containsEvent(STARTED, DefaultTestClassDescriptor, 'TestSuite > FullTest > org.company.SystemOutTest')
        containsEvent(STARTED, DefaultTestMethodDescriptor, 'TestSuite > FullTest > org.company.SystemOutTest > testOut')
        containsEvent(FINISHED, DefaultTestMethodDescriptor, 'TestSuite > FullTest > org.company.SystemOutTest > testOut')
        containsEvent(FINISHED, DefaultTestClassDescriptor, 'TestSuite > FullTest > org.company.SystemOutTest')
        containsEvent(FINISHED, DefaultTestSuiteDescriptor, 'TestSuite > FullTest')
    }

    @ToBeFixedForInstantExecution
    def "synthesized events for broken configuration methods reference test class descriptors"() {
        given:
        file("src/test/java/org/company/TestWithBrokenSetupMethod.java") << """
            package org.company;
    
            import org.testng.Assert;
            import org.testng.annotations.*;
    
            public class TestWithBrokenSetupMethod {
                @BeforeMethod
                public void broken() {
                    throw new RuntimeException();
                }
                @Test
                public void test() {
                }
            }
        """

        when:
        fails 'test'

        then:
        failureCauseContains('There were failing tests')
        containsEvent(STARTED, DefaultTestClassDescriptor, 'Gradle suite > Gradle test > org.company.TestWithBrokenSetupMethod')
        containsEvent(STARTED, DefaultTestMethodDescriptor, 'Gradle suite > Gradle test > org.company.TestWithBrokenSetupMethod > broken')
        containsEvent(FINISHED, DefaultTestMethodDescriptor, 'Gradle suite > Gradle test > org.company.TestWithBrokenSetupMethod > broken')
        containsEvent(STARTED, DefaultTestMethodDescriptor, 'Gradle suite > Gradle test > org.company.TestWithBrokenSetupMethod > test')
        containsEvent(FINISHED, DefaultTestMethodDescriptor, 'Gradle suite > Gradle test > org.company.TestWithBrokenSetupMethod > test')
        containsEvent(FINISHED, DefaultTestClassDescriptor, 'Gradle suite > Gradle test > org.company.TestWithBrokenSetupMethod')
    }

    private boolean containsEvent(String eventType, Class<? extends TestDescriptor> testDescriptorClass, String path) {
        output.readLines().any { it =~ /$eventType event type ${testDescriptorClass.name} for Gradle Test Run :test > Gradle Test Executor \d+ > $path$/ }
    }
}
