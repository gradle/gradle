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
import org.gradle.util.TextUtil
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
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':help')
        !result.standardError
    }

    def "execute build for expected success"() {
        given:
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        !result.standardError
    }

    def "execute build for expected success but fails"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    throw new GradleException('Unexpected exception')
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.succeeds()

        then:
        Throwable t = thrown(UnexpectedBuildFailure)
        String message = TextUtil.normaliseLineSeparators(t.message)
        message.contains('Unexpected build execution failure')
        message.contains("""Reason:
Unexpected exception""")
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
        result.standardOutput.contains(':helloWorld')
        result.standardError.contains('Expected exception')
    }

    def "execute build for expected failure but succeeds"() {
        given:
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.fails()

        then:
        Throwable t = thrown(UnexpectedBuildSuccess)
        t.message.contains('Unexpected build execution success')
    }

    def "execute build for multiple tasks"() {
        given:
        buildFile << helloWorldTask()
        buildFile << """
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
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
        result.standardOutput.contains(':byeWorld')
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
        result.standardOutput.contains(':helloWorld UP-TO-DATE')
        result.standardOutput.contains(':byeWorld SKIPPED')
        !result.standardError
    }

    def "execute plugin and custom task logic as part of the build script"() {
        given:
        buildFile << """
            apply plugin: HelloWorldPlugin

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld', type: HelloWorld)
                }
            }

            class HelloWorld extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println 'Hello world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains('Hello world!')
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


        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':buildSrc:compileJava')
        result.standardOutput.contains(':buildSrc:build')
        result.standardOutput.contains(':helloWorld')
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
        result.standardOutput.contains(':helloWorld')
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

    def "build execution for script with invalid Groovy syntax"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    'Hello world!"
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.fails()

        then:
        noExceptionThrown()
        !result.standardOutput.contains(':helloWorld')
        result.standardError.contains('Could not compile build file')
    }

    def "build execution for script with unknown Gradle API method class"() {
        given:
        buildFile << """
            task helloWorld {
                doSomething {
                    println 'Hello world!'
                }
            }
        """

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        BuildResult result = gradleRunner.fails()

        then:
        noExceptionThrown()
        !result.standardOutput.contains(':helloWorld')
        result.standardError.contains('Could not find method doSomething()')
    }

    def "build execution with badly formed argument"() {
        given:
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.arguments = ['--unknown']
        gradleRunner.succeeds()

        then:
        Throwable t = thrown(UnexpectedBuildFailure)
        String message = TextUtil.normaliseLineSeparators(t.message)
        message.contains("""Reason:
Unknown command-line option '--unknown'.""")
        !message.contains(':helloWorld')
    }

    def "build execution with non-existent working directory"() {
        given:
        File nonExistentWorkingDir = new File('some/path/that/does/not/exist')
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.workingDir = nonExistentWorkingDir
        gradleRunner.succeeds()

        then:
        Throwable t = thrown(UnexpectedBuildFailure)
        String message = TextUtil.normaliseLineSeparators(t.message)
        message.contains("""Reason:
Project directory '$nonExistentWorkingDir.absolutePath' does not exist.""")
        !message.contains(':helloWorld')
    }

    def "build execution with invalid JVM arguments"() {
        given:
        GFileUtils.writeFile('org.gradle.jvmargs=-unknown', testProjectDir.file('gradle.properties'))
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = prepareGradleRunner('helloWorld')
        gradleRunner.succeeds()

        then:
        Throwable t = thrown(UnexpectedBuildFailure)
        String message = TextUtil.normaliseLineSeparators(t.message)
        message.contains("""Reason:
Unable to start the daemon process.
This problem might be caused by incorrect configuration of the daemon.
For example, an unrecognized jvm option is used.""")
        !message.contains(':helloWorld')
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
        Throwable t = thrown(UnexpectedBuildFailure)
        String message = TextUtil.normaliseLineSeparators(t.message)
        message.contains("""Output:
:helloWorld
Hello world!""")
        !message.contains('Bye world!')
        message.contains("""Reason:
Gradle build daemon disappeared unexpectedly (it may have been killed or may have crashed)
""")
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

    private String helloWorldTask() {
        """
        task helloWorld {
            doLast {
                println 'Hello world!'
            }
        }
        """
    }
}
