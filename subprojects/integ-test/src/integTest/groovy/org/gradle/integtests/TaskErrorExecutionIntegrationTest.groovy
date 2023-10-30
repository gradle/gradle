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

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN

class TaskErrorExecutionIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {

    public static final String LIST_OF_PROJECTS = "Run gradle projects to get a list of available projects."
    public static final String GET_TASKS = "Run gradle tasks to get a list of available tasks."
    public static final String NAME_EXPANSION = new DocumentationRegistry().getDocumentationRecommendationFor("on name expansion", "command_line_interface", "sec:name_abbreviation")

    def setup() {
        expectReindentedValidationMessage()
        executer.beforeExecute {
            withStacktraceEnabled()
        }
    }

    def "reports task action execution fails with error"() {
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

    def "reports task action execution fails with runtime exception"() {
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

    def "reports task action execution fails from java with runtime exception"() {
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

    def "reports task injected by other project fails with runtime exception"() {
        createDirs("a", "b")
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

    def "reports task validation failure"() {
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

        failureDescriptionContains("Some problems were found with the configuration of task ':custom' (type 'CustomTask').")
        failureDescriptionContains(missingValueMessage { type('CustomTask').property('srcFile') })
        failureDescriptionContains(missingValueMessage { type('CustomTask').property('destFile') })
    }

    def "reports unknown task"() {
        createDirs("a", "b")
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
        failure.assertHasDescription("Task 'someTest' not found in root project 'test' and its subprojects. Some candidates are: 'someTask', 'someTaskA', 'someTaskB'.")
        failure.assertHasResolutions(
            GET_TASKS,
            NAME_EXPANSION,
            INFO_DEBUG,
            SCAN,
            GET_HELP
        )

        when:
        fails ":someTest"
        then:
        failure.assertHasDescription("Cannot locate tasks that match ':someTest' as task 'someTest' not found in root project 'test'. Some candidates are: 'someTask'.")
        failure.assertHasResolutions(
            GET_TASKS,
            NAME_EXPANSION,
            INFO_DEBUG,
            SCAN,
            GET_HELP
        )

        when:
        fails "a:someTest"
        then:
        failure.assertHasDescription("Cannot locate tasks that match 'a:someTest' as task 'someTest' not found in project ':a'. Some candidates are: 'someTask', 'someTaskA'.")
        failure.assertHasResolutions(
            GET_TASKS,
            NAME_EXPANSION,
            INFO_DEBUG,
            SCAN,
            GET_HELP
        )
    }

    def "reports ambiguous task"() {
        createDirs("a", "b")
        settingsFile << """
            rootProject.name = 'test'
            include 'a', 'b'
        """
        buildFile << """
            allprojects { task someTaskAll }
            project(':a') { task someTaskA }
            project(':b') { task someTaskB }
        """

        when:
        fails "soTa"
        then:
        failure.assertHasDescription("Task 'soTa' is ambiguous in root project 'test' and its subprojects. Candidates are: 'someTaskA', 'someTaskAll', 'someTaskB'.")
        failure.assertHasResolutions(
            GET_TASKS,
            NAME_EXPANSION,
            INFO_DEBUG,
            SCAN,
            GET_HELP
        )

        when:
        fails "a:soTa"
        then:
        failure.assertHasDescription("Cannot locate tasks that match 'a:soTa' as task 'soTa' is ambiguous in project ':a'. Candidates are: 'someTaskA', 'someTaskAll'.")
        failure.assertHasResolutions(
            GET_TASKS,
            NAME_EXPANSION,
            INFO_DEBUG,
            SCAN,
            GET_HELP
        )
    }

    def "reports unknown project"() {
        createDirs("projA", "projB")
        settingsFile << """
            rootProject.name = 'test'
            include 'projA', 'projB'
        """
        buildFile << """
            allprojects { task someTask }
        """

        when:
        fails "prog:someTask"

        then:
        failure.assertHasDescription("Cannot locate tasks that match 'prog:someTask' as project 'prog' not found in root project 'test'. Some candidates are: 'projA', 'projB'.")
        failure.assertHasResolutions(
            LIST_OF_PROJECTS,
            NAME_EXPANSION,
            INFO_DEBUG,
            SCAN,
            GET_HELP
        )
    }

    def "reports ambiguous project"() {
        createDirs("projA", "projB")
        settingsFile << """
            rootProject.name = 'test'
            include 'projA', 'projB'
        """
        buildFile << """
            allprojects { task someTask }
        """

        when:
        fails "proj:someTask"

        then:
        failure.assertHasDescription("Cannot locate tasks that match 'proj:someTask' as project 'proj' is ambiguous in root project 'test'. Candidates are: 'projA', 'projB'.")
        failure.assertHasResolutions(
            LIST_OF_PROJECTS,
            NAME_EXPANSION,
            INFO_DEBUG,
            SCAN,
            GET_HELP
        )
    }
}
