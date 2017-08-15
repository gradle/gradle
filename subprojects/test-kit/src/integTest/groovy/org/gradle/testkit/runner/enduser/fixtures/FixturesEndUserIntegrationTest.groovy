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

package org.gradle.testkit.runner.enduser.fixtures

import org.gradle.testkit.runner.enduser.BaseTestKitEndUserIntegrationTest

class FixturesEndUserIntegrationTest extends BaseTestKitEndUserIntegrationTest {

    def "can use JUnit-based test fixture"() {
        given:
        buildFile << basicJavaBuild()
        buildFile << junitDependency()

        file("src/test/java/EndUserFunctionalTest.java") << """
            import org.gradle.testkit.runner.BuildResult;
            import org.gradle.testkit.runner.fixtures.JUnit4FunctionalTest;

            import org.junit.Test;
            
            import static org.junit.Assert.assertEquals;
            import static org.junit.Assert.assertTrue;

            import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
            
            public class EndUserFunctionalTest extends JUnit4FunctionalTest {
                ${javaBasedTestCase()}
            }
        """

        when:
        succeeds 'test'

        then:
        outputContains('Running test: EndUserFunctionalTest.canExecuteSuccessfulBuild')
    }

    def "can use TestNG-based test fixture"() {
        given:
        buildFile << basicJavaBuild()
        buildFile << testngDependency()

        file("src/test/java/EndUserFunctionalTest.java") << """
            import org.gradle.testkit.runner.BuildResult;
            import org.gradle.testkit.runner.fixtures.TestNGFunctionalTest;

            import org.testng.annotations.Test;
            
            import static org.testng.Assert.assertEquals;
            import static org.testng.Assert.assertTrue;

            import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
            
            public class EndUserFunctionalTest extends TestNGFunctionalTest {
                ${javaBasedTestCase()}
            }
        """

        when:
        succeeds 'test'

        then:
        outputContains('Running test: EndUserFunctionalTest.canExecuteSuccessfulBuild')
    }

    def "can use Spock-based test fixture"() {
        given:
        buildFile << basicJavaBuild()
        buildFile << groovyDependency()
        buildFile << spockDependency()

        file("src/test/groovy/EndUserFunctionalTest.groovy") << """
            import org.gradle.testkit.runner.fixtures.SpockFunctionalTest

            import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
            
            class EndUserFunctionalTest extends SpockFunctionalTest {
                def "can execute successful build"() {
                    given:
                    buildFile << '''
                        task helloWorld {
                            doLast {
                                println 'Hello World!'
                            }
                        }
                    '''
                    
                    when:
                    gradleRunner.withDebug($debug)
                    def result = succeeds('helloWorld')

                    then:
                    result.task(':helloWorld').outcome == SUCCESS
                    result.output.contains('Hello World!')
                }
            }
        """

        when:
        succeeds 'test'

        then:
        outputContains('Running test: EndUserFunctionalTest.can execute successful build')
    }

    def "can use trait-based test fixture"() {
        given:
        buildFile << basicJavaBuild()
        buildFile << junitDependency()
        buildFile << groovyDependency()

        file("src/test/groovy/EndUserFunctionalTest.groovy") << """
            import org.junit.Test

            import org.gradle.testkit.runner.fixtures.FunctionalTestSupportTrait

            import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
            
            class EndUserFunctionalTest implements FunctionalTestSupportTrait {
                @Test
                void canExecuteSuccessfulBuild() {
                    buildFile << '''
                        task helloWorld {
                            doLast {
                                println 'Hello World!'
                            }
                        }
                    '''

                    gradleRunner.withDebug($debug)
                    def result = succeeds('helloWorld')

                    assert result.task(':helloWorld').outcome == SUCCESS
                    assert result.output.contains('Hello World!')
                }
            }
        """

        when:
        succeeds 'test'

        then:
        outputContains('Running test: EndUserFunctionalTest.canExecuteSuccessfulBuild')
    }

    static String basicJavaBuild() {
        """
            apply plugin: 'java'
            
            repositories {
                jcenter()
            }

            dependencies {
                testCompile gradleTestKit()
            }
            
            test {
                beforeTest { descriptor ->
                    logger.lifecycle("Running test: \${descriptor.className}.\${descriptor.name}")
                }
            }
        """
    }

    static String junitDependency() {
        """
            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """
    }

    static String groovyDependency() {
        """
            apply plugin: 'groovy'
            
            dependencies {
                testCompile localGroovy()
            }
        """
    }

    static String spockDependency() {
        """
            dependencies {
                testCompile('org.spockframework:spock-core:1.0-groovy-2.4') {
                    exclude module: 'groovy-all'
                }
            }
        """
    }

    static String testngDependency() {
        """
            dependencies {
                testCompile 'org.testng:testng:6.9.9'
            }
            
            test {
                useTestNG()
            }
        """
    }

    static String javaBasedTestCase() {
        """
            @Test
            public void canExecuteSuccessfulBuild() {
                String buildFileContent = "task helloWorld {" +
                              "    doLast {" +
                              "        println 'Hello world!'" +
                              "    }" +
                              "}";
                getBuildFile().setText(buildFileContent);
                
                getGradleRunner().withDebug($debug);
                BuildResult result = succeeds("helloWorld");

                assertTrue(result.getOutput().contains("Hello world!"));
                assertEquals(result.task(":helloWorld").getOutcome(), SUCCESS);
            }
        """
    }
}
