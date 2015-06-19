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

package org.gradle.testkit.functional

import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.functional.internal.dist.InstalledGradleDistribution
import org.gradle.util.GFileUtils
import org.junit.Rule
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class GradleRunnerIntegrationTest extends Specification {
    @Shared IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    @Rule TestNameTestDirectoryProvider testProjectDir = new TestNameTestDirectoryProvider()
    File buildFile

    def setup() {
        buildFile = testProjectDir.file('build.gradle')
    }

    def "execute build without providing a task runs the help task"() {
        when:
        GradleRunner gradleRunner = prepareGradleRunner()
        gradleRunner.succeeds()

        then:
        noExceptionThrown()
    }

    def "execute build for expected success"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains('Hello world!')
        !result.standardError
    }

    def "execute build for expected success but fails"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    throws new GradleException('Unexpected exception')
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.succeeds()

        then:
        Throwable t = thrown(UnexpectedBuildFailure)
        t.message == 'Unexpected build execution failure'
    }

    def "execute build for expected failure"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    throw new GradleException('Expected exception')
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.fails()

        then:
        noExceptionThrown()
        result.standardOutput
        result.standardError.contains('Expected exception')
    }

    def "execute build for expected failure but succeeds"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.fails()

        then:
        Throwable t = thrown(UnexpectedBuildSuccess)
        t.message == 'Unexpected build execution success'
    }

    def "execute build for multiple tasks"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }

            task byeWorld {
                doLast {
                    println 'Bye world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld', 'byeWorld')
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains('Hello world!')
        result.standardOutput.contains('Bye world!')
        !result.standardError
    }

    def "execute skipped tasks"() {
        given:
        buildFile << """
            task helloWorld

            task byeWorld {
                onlyIf {
                    false
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld', 'byeWorld')
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput
        !result.standardError
    }

    def "execute build with buildSrc project"() {
        given:
        File buildSrcJavaSrcDir = testProjectDir.createDir('buildSrc', 'src', 'main', 'java', 'org', 'gradle', 'test')
        GFileUtils.writeFile("""package org.gradle.test;

public class MyApp {
    public static void main(String args[]) {
       System.out.println("Hello world!");
    }
}
""", new File(buildSrcJavaSrcDir, 'MyApp.java'))


        buildFile << """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':buildSrc:compileJava')
        result.standardOutput.contains(':buildSrc:build')
        result.standardOutput.contains('Hello world!')
        !result.standardError
    }

    @Unroll
    def "can provide arguments #arguments for build execution"() {
        given:
        final String debugMessage = 'Some debug message'
        final String infoMessage = 'My property: ${project.hasProperty("myProp") ? project.getProperty("myProp") : null}'
        final String quietMessage = 'Log in any case'

        buildFile << """
            task helloWorld {
                doLast {
                    logger.debug '$debugMessage'
                    logger.info '$infoMessage'
                    logger.quiet '$quietMessage'
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.arguments = arguments
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains(debugMessage) == hasDebugMessage
        result.standardOutput.contains(infoMessage) == hasInfoMessage
        result.standardOutput.contains(quietMessage) == hasQuietMessage

        where:
        arguments                | hasDebugMessage | hasInfoMessage | hasQuietMessage
        []                       | false           | false          | true
        ['-PmyProp=hello']       | false           | false          | true
        ['-d', '-PmyProp=hello'] | true            | true           | true
        ['-i', '-PmyProp=hello'] | false           | true           | true
    }

    def "daemon dies during build execution"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                    Runtime.runtime.halt(0)
                    println 'Bye world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.succeeds()

        then:
        thrown(UnexpectedBuildFailure)
    }

    private GradleRunner prepareGradleRunner(String... tasks) {
        GradleRunner gradleRunner = GradleRunner.create(new InstalledGradleDistribution(buildContext.gradleHomeDir))

        gradleRunner.with {
            gradleUserHomeDir = buildContext.gradleUserHomeDir
            workingDir = testProjectDir.testDirectory
            setTasks(tasks as List<String>)
        }

        assert gradleRunner.workingDir == testProjectDir.testDirectory
        gradleRunner
    }
}
