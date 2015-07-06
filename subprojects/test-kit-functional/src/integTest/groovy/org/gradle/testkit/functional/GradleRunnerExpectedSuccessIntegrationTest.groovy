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

import org.gradle.util.GFileUtils
import org.gradle.util.TextUtil
import spock.lang.Unroll

class GradleRunnerExpectedSuccessIntegrationTest extends AbstractGradleRunnerIntegrationTest {
    def "execute build without providing a task runs the help task"() {
        when:
        GradleRunner gradleRunner = prepareGradleRunner()
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':help')
        !result.standardError
        result.executedTasks == [':help']
        result.skippedTasks.empty
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
        result.executedTasks == [':helloWorld']
        result.skippedTasks.empty
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
        String expectedMessage = """Unexpected build execution failure in $gradleRunner.workingDir with tasks \\[helloWorld\\] and arguments \\[\\]

Output:
:helloWorld FAILED

BUILD FAILED

Total time: .+ secs

-----
Error:

FAILURE: Build failed with an exception\\.

\\* Where:
Build file '$gradleRunner.workingDir/build\\.gradle' line: 4

\\* What went wrong:
Execution failed for task ':helloWorld'\\.
> Unexpected exception

\\* Try:
Run with --stacktrace option to get the stack trace\\. Run with --info or --debug option to get more log output.

-----
Reason:
Unexpected exception
-----"""
        TextUtil.normaliseLineSeparators(t.message) ==~ expectedMessage
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
        result.executedTasks == [':helloWorld', ':byeWorld']
        result.skippedTasks.empty
    }

    def "execute task actions marked as up-to-date or skipped"() {
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
        result.executedTasks == [':helloWorld', ':byeWorld']
        result.skippedTasks == [':helloWorld', ':byeWorld']
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
        result.executedTasks == [':helloWorld']
        result.skippedTasks.empty
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
        result.executedTasks == [':helloWorld']
        result.skippedTasks.empty
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
        gradleRunner.withArguments(arguments)
        BuildResult result = gradleRunner.succeeds()

        then:
        noExceptionThrown()
        result.standardOutput.contains(':helloWorld')
        result.standardOutput.contains(debugMessage) == hasDebugMessage
        result.standardOutput.contains(infoMessage) == hasInfoMessage
        result.standardOutput.contains(quietMessage) == hasQuietMessage
        result.executedTasks == [':helloWorld']
        result.skippedTasks.empty

        where:
        arguments                | hasDebugMessage | hasInfoMessage | hasQuietMessage
        []                       | false           | false          | true
        ['-PmyProp=hello']       | false           | false          | true
        ['-d', '-PmyProp=hello'] | true            | true           | true
        ['-i', '-PmyProp=hello'] | false           | true           | true
    }
}
