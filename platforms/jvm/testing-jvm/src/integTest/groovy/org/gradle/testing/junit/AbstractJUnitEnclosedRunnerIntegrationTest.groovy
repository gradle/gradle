/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.testing.junit

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import spock.lang.Issue

abstract class AbstractJUnitEnclosedRunnerIntegrationTest extends AbstractTestingMultiVersionIntegrationTest {
    def setup() {
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """.stripIndent()
    }

    @Issue('https://github.com/gradle/gradle/issues/2319')
    def 'can run tests in Enclosed runner'() {
        given:
        file('src/test/java/EnclosedTest.java') << """
            ${testFrameworkImports}
            import org.junit.experimental.runners.Enclosed;

            @RunWith( Enclosed.class )
            public class EnclosedTest {
                public static class InnerClass {
                   @Test
                    public void aTest() {
                        Assert.assertEquals( "test", "test" );
                    }
                }
            }
        """.stripIndent()

        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClass('EnclosedTest$InnerClass').assertTestCount(1, 0, 0)
    }

    @Issue('https://github.com/gradle/gradle/issues/2320')
    def 'can run @BeforeClass in Enclosed runner'() {
        given:
        file('src/test/java/EnclosedTest.java') << """
            ${testFrameworkImports}
            import org.junit.experimental.runners.Enclosed;

            @RunWith( Enclosed.class )
            public class EnclosedTest {
                private static String someValue;

                @BeforeClass
                public static void setSomeValue() {
                    someValue = "test";
                }

                public static class InnerClass {
                    @Test
                    public void aTest() {
                        Assert.assertEquals( "test", someValue );
                    }
                }
            }
        """.stripIndent()
        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClass('EnclosedTest$InnerClass').assertTestCount(1, 0, 0)
    }

    @Issue('https://github.com/junit-team/junit4/issues/1354')
    def 'can run tests in Enclosed runner with Category'() {
        given:
        file('src/test/java/EnclosedTest.java') << """
            ${testFrameworkImports}
            import org.junit.experimental.runners.Enclosed;
            import org.junit.experimental.categories.Category;

            @RunWith(Enclosed.class)
            public class EnclosedTest {
              private static int outer;

              @BeforeClass
              public static void runBeforeEnclosedSuite() {
                  outer = 1;
              }

              @Category(Fast.class)
              public static class InnerClass {
                private int inner;

                @Before
                public void setUp() {
                   inner = 2;
                }

                @Test
                public void test() {
                   Assert.assertTrue(outer==1 && inner==2);
                }
              }
            }
        """.stripIndent()
        file('src/test/java/Fast.java') << 'public interface Fast {}'
        buildFile << '''
            test {
                useJUnit {
                    includeCategories 'Fast'
                }
            }
        '''

        when:
        succeeds('test')

        then:
        new DefaultTestExecutionResult(testDirectory)
            .testClass('EnclosedTest$InnerClass').assertTestCount(1, 0, 0)
    }
}
