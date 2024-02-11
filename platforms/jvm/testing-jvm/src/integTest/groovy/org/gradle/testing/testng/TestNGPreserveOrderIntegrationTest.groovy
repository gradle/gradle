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

@TargetCoverage({ TestNGCoverage.SUPPORTS_PRESERVE_ORDER })
public class TestNGPreserveOrderIntegrationTest extends MultiVersionIntegrationSpec {

    def "run tests using preserveOrder"() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'org.testng:testng:$version' }
            test {
                useTestNG { preserveOrder true }
                onOutput { test, event -> print "\$event.message" }
            }
        """

        file("src/test/java/Test1.java") << """
            import org.testng.annotations.AfterClass;
            import org.testng.annotations.BeforeClass;
            import org.testng.annotations.Test;

            public class Test1 {

                @BeforeClass
                public void beforeClass() {
                    System.out.println("Test1.beforeClass()");
                }

                @Test
                public void test1() {
                    System.out.println("Test1.test1()");
                }

                @Test(dependsOnMethods = {"test1"})
                public void test2() {
                    System.out.println("Test1.test2()");
                }

                @AfterClass
                public void afterClass() {
                    System.out.println("Test1.afterClass()");
                }
            }
        """

        file("src/test/java/Test2.java") << """
            import java.io.Serializable;

            import org.testng.annotations.AfterClass;
            import org.testng.annotations.BeforeClass;
            import org.testng.annotations.Test;

            public class Test2 {

                public static class C implements Serializable {
                    private static final long serialVersionUID = 1L;
                }

                @BeforeClass
                public void beforeClass() {
                    System.out.println("Test2.beforeClass()");
                }

                @Test
                public void test1() {
                    System.out.println("Test2.test1()");
                }

                @Test(dependsOnMethods = {"test1"})
                public void test2() {
                    System.out.println("Test2.test2()");
                }

                @AfterClass
                public void afterClass() {
                    System.out.println("Test2.afterClass()");
                }
            }
        """

        when: succeeds "test"

        then:
        outputContains("""
Test1.beforeClass()
Test1.test1()
Test1.test2()
Test1.afterClass()
""")
        outputContains("""
Test2.beforeClass()
Test2.test1()
Test2.test2()
Test2.afterClass()
""")
    }
}
