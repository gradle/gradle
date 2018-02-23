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
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.TestNGCoverage

import static org.gradle.testing.fixture.TestNGCoverage.FIXED_ICLASS_LISTENER
import static org.gradle.testing.fixture.TestNGCoverage.NEWEST
import static org.gradle.util.TextUtil.normaliseFileSeparators

@TargetCoverage({ [FIXED_ICLASS_LISTENER, NEWEST] })
class TestNGClassIntegrationTest extends MultiVersionIntegrationSpec {

    private final static String STARTED_EVENT_DESCRIPTOR = 'Started'
    private final static String FINISHED_EVENT_DESCRIPTOR = 'Finished'

    def setup() {
        executer.noExtraLogging()
        TestNGCoverage.enableTestNG(buildFile, version)
    }

    def "test class events references correct suite as parent"() {
        given:
        def testNgSuite = file("src/test/resources/testng.xml")

        buildFile << """
            import org.gradle.api.internal.tasks.testing.TestCompleteEvent
            import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
            import org.gradle.api.internal.tasks.testing.TestStartEvent
            import org.gradle.api.internal.tasks.testing.results.TestListenerInternal
            import org.gradle.api.tasks.testing.TestOutputEvent
            import org.gradle.api.tasks.testing.TestResult
    
            test {
                useTestNG {
                    suites file("${(normaliseFileSeparators(testNgSuite.absolutePath))}")
                }
            }
    
            gradle.addListener(new TestListenerInternal() {
                void started(TestDescriptorInternal d, TestStartEvent s) {
                    printEventInformation('$STARTED_EVENT_DESCRIPTOR', d)
                }
    
                void completed(TestDescriptorInternal d, TestResult result, TestCompleteEvent cE) {
                    printEventInformation('$FINISHED_EVENT_DESCRIPTOR', d)
                }
    
                private void printEventInformation(String eventTypeDescription, TestDescriptorInternal d) {
                    println "\$eventTypeDescription event type \${d.descriptor.getClass().getName()} with descriptor \${d.name} and parent \${d.parent?.name}"
                }
    
                void output(TestDescriptorInternal descriptor, TestOutputEvent output) {
                }
            })
        """

        file("src/test/java/org/company/SystemOutTest.java") << """
            package org.company;
    
            import org.testng.Assert;
            import org.testng.annotations.Test;
    
            @Test
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
        containsTestClassEvent(STARTED_EVENT_DESCRIPTOR, 'LightTest')
        containsTestClassEvent(STARTED_EVENT_DESCRIPTOR, 'FullTest')
        containsTestClassEvent(FINISHED_EVENT_DESCRIPTOR, 'LightTest')
        containsTestClassEvent(FINISHED_EVENT_DESCRIPTOR, 'FullTest')
    }

    private boolean containsTestClassEvent(String eventTypeDescription, String parent) {
        output.contains("$eventTypeDescription event type ${DefaultTestClassDescriptor.getName()} with descriptor org.company.SystemOutTest and parent $parent")
    }
}
