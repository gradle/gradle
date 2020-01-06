/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class TaskErrorExecutionIntegrationTest extends AbstractIntegrationSpec {
    def reportsTaskActionExecutionFailsWithError() {
        buildFile << """
            task('do-stuff').doFirst {
                throw new ArithmeticException('broken')
            }
        """
        expect:
        fails "do-stuff"

        failure.assertHasFileName(String.format("Build file '%s'", buildFile))
        failure.assertHasLineNumber(3)
        failure.assertHasDescription("Execution failed for task ':do-stuff'.")
        failure.assertHasCause("broken")
    }

    def reportsTaskActionExecutionFailsWithRuntimeException() {
        buildFile << """
            task brokenClosure {
                doLast {
                    throw new RuntimeException('broken closure')
                }
            }
        """
        expect:
        fails "brokenClosure"

        failure.assertHasFileName(String.format("Build file '%s'", buildFile))
        failure.assertHasLineNumber(4)
        failure.assertHasDescription("Execution failed for task ':brokenClosure'.")
        failure.assertHasCause("broken closure")
    }

    def reportsTaskActionExecutionFailsFromJavaWithRuntimeException() {
        file("buildSrc/src/main/java/org/gradle/BrokenTask.java") << """
            package org.gradle;

            import org.gradle.api.Action;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Task;

            public class BrokenTask extends DefaultTask {
                public BrokenTask() {
                    doFirst(new Action<Task>() {
                        public void execute(Task task) {
                            throw new RuntimeException("broken action");
                        }
                    });
                }
            }
        """
        buildFile << """
            task brokenJavaTask(type: org.gradle.BrokenTask)
        """

        expect:
        fails "brokenJavaTask"

        failure.assertHasDescription("Execution failed for task ':brokenJavaTask'.")
        failure.assertHasCause("broken action")
    }

    def reportsTaskInjectedByOtherProjectFailsWithRuntimeException() {
        file("settings.gradle") << "include 'a', 'b'"
        TestFile buildFile = file("b/build.gradle")
        buildFile << """
            project(':a') {
                task a {
                    doLast {
                         throw new RuntimeException('broken')
                    }
                }
            }
        """
        expect:
        fails "a"

        failure.assertHasFileName(String.format("Build file '%s'", buildFile))
        failure.assertHasLineNumber(5)
        failure.assertHasDescription("Execution failed for task ':a:a'.")
        failure.assertHasCause("broken")
    }

    def reportsTaskValidationFailure() {
        buildFile << '''
            class CustomTask extends DefaultTask {
                @InputFile File srcFile
                @OutputFile File destFile
                
                @TaskAction
                void action() {}
            }
            
            task custom(type: CustomTask)
        '''
        expect:
        fails "custom"

        failure.assertHasDescription("Some problems were found with the configuration of task ':custom' (type 'CustomTask').")
        failure.assertHasCause("No value has been specified for property 'srcFile'.")
        failure.assertHasCause("No value has been specified for property 'destFile'.")
    }

    def reportsUnknownTask() {
        settingsFile << """
            rootProject.name = 'test'
            include 'a', 'b'
        """
        buildFile << """
            allprojects { task someTask }
            project(':a') { task someTaskA }
            project(':b') { task someTaskB }
        """

        when:
        fails "someTest"

        then:
        failure.assertHasDescription("Task 'someTest' not found in root project 'test'. Some candidates are: 'someTask', 'someTaskA', 'someTaskB'.")
        failure.assertHasResolution("Run gradle tasks to get a list of available tasks. Run with --info or --debug option to get more log output. Run with --scan to get full insights.")

        when:
        fails ":someTest"
        then:
        failure.assertHasDescription("Task 'someTest' not found in root project 'test'. Some candidates are: 'someTask'.")
        failure.assertHasResolution("Run gradle tasks to get a list of available tasks. Run with --info or --debug option to get more log output. Run with --scan to get full insights.")

        when:
        fails "a:someTest"
        then:
        failure.assertHasDescription("Task 'someTest' not found in project ':a'. Some candidates are: 'someTask', 'someTaskA'.")
        failure.assertHasResolution("Run gradle tasks to get a list of available tasks. Run with --info or --debug option to get more log output. Run with --scan to get full insights.")
    }
}
