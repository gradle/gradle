/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.TestNGCoverage

@TargetCoverage({ TestNGCoverage.SUPPORTS_GROUP_BY_INSTANCES })
public class TestNGGroupByInstancesIntegrationTest extends MultiVersionIntegrationSpec {

    def "run tests using groupByInstances"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'org.testng:testng:$version' }
            test {
                useTestNG {
                    suiteName = 'Suite Name'
                    testName = 'Test Name'
                    groupByInstances = true
                }
                onOutput { test, event -> print "\$event.message" }
            }
        """

        file("src/test/java/TestFactory.java") << """
            import org.testng.annotations.AfterClass;
            import org.testng.annotations.BeforeClass;
            import org.testng.annotations.DataProvider;
            import org.testng.annotations.Factory;
            import org.testng.annotations.Test;

            public class TestFactory {

                @DataProvider(name = "data")
                public static Object[][] provide() {
                    return new Object[][] {
                        { "data1" },
                        { "data2" }
                    };
                }

                private String data;

                @Factory(dataProvider = "data")
                public TestFactory(String data) {
                    this.data = data;
                }

                @BeforeClass
                public void beforeClass() {
                    System.out.println("TestFactory[" + data + "].beforeClass()");
                }

                @Test
                public void test1() {
                    System.out.println("TestFactory[" + data + "].test1()");
                }

                @Test(dependsOnMethods = {"test1"})
                public void test2() {
                    System.out.println("TestFactory[" + data + "].test2()");
                }

                @AfterClass
                public void afterClass() {
                    System.out.println("TestFactory[" + data + "].afterClass()");
                }
            }
        """

        when: succeeds "test"

        then:
        outputContains("""
TestFactory[data1].beforeClass()
TestFactory[data1].test1()
TestFactory[data1].test2()
TestFactory[data1].afterClass()
""")
        outputContains("""
TestFactory[data2].beforeClass()
TestFactory[data2].test1()
TestFactory[data2].test2()
TestFactory[data2].afterClass()
""")
    }
}
