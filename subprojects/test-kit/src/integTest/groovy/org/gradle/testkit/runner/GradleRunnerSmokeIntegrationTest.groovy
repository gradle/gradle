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

package org.gradle.testkit.runner

import org.gradle.launcher.exec.DaemonUsageSuggestingBuildActionExecuter
import org.gradle.testkit.runner.fixtures.CaptureBuildOutputInDebug
import org.gradle.util.GFileUtils

import static org.gradle.testkit.runner.TaskOutcome.*

@CaptureBuildOutputInDebug
class GradleRunnerSmokeIntegrationTest extends AbstractGradleRunnerCompatibilityIntegrationTest {

    def "execute build for expected success"() {
        given:
        buildFile << helloWorldTask()

        when:
        GradleRunner gradleRunner = runner('helloWorld')
        BuildResult result = gradleRunner.build()

        then:
        noExceptionThrown()
        result.output.contains(':helloWorld')
        result.output.contains('Hello world!')
        result.tasks.collect { it.path } == [':helloWorld']
        result.taskPaths(SUCCESS) == [':helloWorld']
        result.taskPaths(SKIPPED).empty
        result.taskPaths(UP_TO_DATE).empty
        result.taskPaths(FAILED).empty
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
        def result = runner('helloWorld')
            .build()

        then:
        result.taskPaths(SUCCESS) == [':helloWorld']
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
        def result = runner('helloWorld').build()

        then:
        result.output.contains('Hello world!')
        result.taskPaths(SUCCESS) == [':helloWorld']
    }

    def "build output does not include daemon usage suggestion"() {
        when:
        buildFile << "task foo"
        def result = runner("foo").build()

        then:
        !result.output.contains(DaemonUsageSuggestingBuildActionExecuter.PLEASE_USE_DAEMON_MESSAGE_PREFIX)
    }

}
