/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JacocoAggregationIntegrationTest extends AbstractIntegrationSpec {
    def "can aggregate jacoco execution data from subprojects"() {
        multiProjectBuild("root", ["application", "direct", "transitive"]) {
            buildFile << """
                subprojects {
                    repositories {
                        ${mavenCentralRepository()}
                    }

                    plugins.withId("jvm-test-suite") {
                        testing {
                            suites {
                                test {
                                    dependencies {
                                        implementation "junit:junit:4.13"
                                    }
                                }
                            }
                        }
                    }
                }
            """
            file("application/build.gradle") << """
                plugins {
                    id 'java'
                    id 'jacoco'
                    id 'org.gradle.jacoco-report-aggregation'
                }

                dependencies {
                    implementation project(":direct")
                    coverageDataPathForTest project
                }
            """

            file("application/src/main/java/application/Adder.java").java """
                public class Adder {
                    int add(int x, int y) {
                        return x+y;
                    }
                }
            """
            file("application/src/test/java/application/AdderTest.java").java """
                import org.junit.Assert;
                import org.junit.Test;
                
                public class AdderTest {
                    @Test 
                    void testAdd() {
                        Adder adder = new Adder();
                        Assert.assertEquals(2, adder.add(1, 1));
                        Assert.assertEquals(4, adder.add(2, 2));
                        Assert.assertEquals(3, adder.add(1, 2));
                    }
                }
            """

            file("direct/build.gradle") << """
                plugins {
                    id 'java'
                    id 'jacoco'
                }

                dependencies {
                    implementation project(":transitive")
                }
            """
            file("direct/src/main/java/direct/Direct.java").java """
                public class Direct {
                }
            """
            file("direct/src/test/java/direct/DirectTest.java").java """
                public class DirectTest {
                }
            """
            file("transitive/build.gradle") << """
                plugins {
                    id 'java'
                    id 'jacoco'
                }
            """
            file("transitive/src/main/java/transitive/Transitive.java").java """
                public class Transitive {

                }
            """
            file("transitive/src/test/java/transitive/TransitiveTest.java").java """
                public class TransitiveTest {

                }
            """
        }
        when:
        succeeds(":application:testCodeCoverageReport")
        then:
        file("application/build/reports/").assertExists()
    }
}
