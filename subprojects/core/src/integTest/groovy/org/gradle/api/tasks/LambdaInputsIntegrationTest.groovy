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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

class LambdaInputsIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker, DirectoryBuildCacheFixture {

    def "implementation of nested property in Groovy build script is tracked"() {
        setupTaskClassWithActionProperty()
        buildFile << """
            task myTask(type: TaskWithActionProperty) {
                action = ${originalImplementation}
            }
        """

        buildFile.makeOlder()

        when:
        run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        run 'myTask'
        then:
        skipped(':myTask')

        when:
        buildFile.text = """
            task myTask(type: TaskWithActionProperty) {
                action = ${changedImplementation}
            }
        """
        run 'myTask', '--info'
        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "changed"
        output.contains "Implementation of input property 'action' has changed for task ':myTask'"

        where:
        originalImplementation                  | changedImplementation
        '{ it.text = "hello" }'                 | '{ it.text = "changed" }'
        wrapAction('outputFile.text = "hello"') | wrapAction('outputFile.text = "changed"')
    }

    private static String wrapAction(String body) {
        """
            new Action() {
                void execute(outputFile) {
                    ${body}
                }
            }
        """
    }

    @ValidationTestFor(
        ValidationProblemId.UNKNOWN_IMPLEMENTATION
    )
    @Issue("https://github.com/gradle/gradle/issues/5510")
    def "task with nested property defined by Java lambda disables execution optimizations"() {
        setupTaskClassWithActionProperty()
        def originalClassName = "LambdaActionOriginal"
        def changedClassName = "LambdaActionChanged"
        file("buildSrc/src/main/java/${originalClassName}.java") << javaClass(originalClassName, lambdaWritingFile("ACTION", "original"))
        file("buildSrc/src/main/java/${changedClassName}.java") << javaClass(changedClassName, lambdaWritingFile("ACTION", "changed"))
        buildFile << """
            task myTask(type: TaskWithActionProperty) {
                action = providers.gradleProperty("changed").isPresent()
                    ? ${changedClassName}.ACTION
                    : ${originalClassName}.ACTION
            }
        """

        buildFile.makeOlder()

        when:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown {
            nestedProperty('action')
            implementedByLambda('LambdaActionOriginal')
            includeLink()
        })
        run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown {
            nestedProperty('action')
            implementedByLambda('LambdaActionOriginal')
            includeLink()
        })
        run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown {
            nestedProperty('action')
            implementedByLambda('LambdaActionChanged')
            includeLink()
        })
        run 'myTask', '-Pchanged'
        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "changed"
    }

    @ValidationTestFor(
        ValidationProblemId.UNKNOWN_IMPLEMENTATION
    )
    def "can change nested property from Java lambda to anonymous inner class and back"() {
        setupTaskClassWithActionProperty()
        def lambdaClassName = "LambdaAction"
        def anonymousClassName = "AnonymousAction"
        file("buildSrc/src/main/java/${lambdaClassName}.java") << javaClass(lambdaClassName, lambdaWritingFile("ACTION", "lambda"))
        file("buildSrc/src/main/java/${anonymousClassName}.java") << javaClass(anonymousClassName, anonymousClassWritingFile("ACTION", "anonymous"))
        buildFile << """
            task myTask(type: TaskWithActionProperty) {
                outputs.cacheIf { true }
                action = providers.gradleProperty("anonymous").isPresent()
                    ? ${anonymousClassName}.ACTION
                    : ${lambdaClassName}.ACTION
            }
        """

        buildFile.makeOlder()

        when:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown {
            nestedProperty('action')
            implementedByLambda('LambdaAction')
            includeLink()
        })
        run 'myTask'
        then:
        withBuildCache().executedAndNotSkipped(':myTask')

        when:
        run 'myTask', '-Panonymous'
        then:
        withBuildCache().executedAndNotSkipped(':myTask')

        when:
        withBuildCache().run 'myTask', '-Panonymous'
        then:
        skipped(':myTask')

        when:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown {
            nestedProperty('action')
            implementedByLambda('LambdaAction')
            includeLink()
        })
        withBuildCache().run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        withBuildCache().run 'myTask', '-Panonymous'
        then:
        skipped(':myTask')
    }

    private TestFile setupTaskClassWithActionProperty() {
        file("buildSrc/src/main/java/TaskWithActionProperty.java") << """
            import org.gradle.api.Action;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.NonNullApi;
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.tasks.OutputFile;
            import org.gradle.api.tasks.TaskAction;

            import java.io.File;

            @NonNullApi
            public class TaskWithActionProperty extends DefaultTask {
                private File outputFile = new File(getTemporaryDir(), "output.txt");
                private Action<File> action;

                @OutputFile
                public File getOutputFile() {
                    return outputFile;
                }

                public void setOutputFile(File outputFile) {
                    this.outputFile = outputFile;
                }

                @Nested
                public Action<File> getAction() {
                    return action;
                }

                public void setAction(Action<File> action) {
                    this.action = action;
                }

                @TaskAction
                public void doStuff() {
                    getAction().execute(outputFile);
                }
            }
        """
    }

    @ValidationTestFor(
        ValidationProblemId.UNKNOWN_IMPLEMENTATION
    )
    @Issue("https://github.com/gradle/gradle/issues/5510")
    def "task with Java lambda actions disables execution optimizations"() {
        file("buildSrc/src/main/java/LambdaActionOriginal.java") << javaClass("LambdaActionOriginal", lambdaPrintingString("ACTION", "From Lambda: original"))
        file("buildSrc/src/main/java/LambdaActionChanged.java") << javaClass("LambdaActionChanged", lambdaPrintingString("ACTION", "From Lambda: changed"))

        setupCustomTask()

        def script = """
            task myTask(type: CustomTask)
        """

        buildFile << script <<
            """
            myTask.doLast(
                providers.gradleProperty("changed").isPresent()
                    ? LambdaActionChanged.ACTION
                    : LambdaActionOriginal.ACTION
            )
        """

        when:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown { additionalTaskAction(':myTask').implementedByLambda('LambdaActionOriginal').includeLink() })
        run "myTask"
        then:
        executedAndNotSkipped(":myTask")

        when:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown { additionalTaskAction(':myTask').implementedByLambda('LambdaActionOriginal').includeLink() })
        run "myTask"
        then:
        executedAndNotSkipped(":myTask")

        when:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown { additionalTaskAction(':myTask').implementedByLambda('LambdaActionChanged').includeLink() })
        run "myTask", "-Pchanged"
        then:
        executedAndNotSkipped(":myTask")
    }

    @ValidationTestFor(
        ValidationProblemId.UNKNOWN_IMPLEMENTATION
    )
    def "can change lambda action to anonymous inner class and back"() {
        file("buildSrc/src/main/java/LambdaAction.java") << javaClass("LambdaAction", lambdaPrintingString("ACTION", "From Lambda"))
        file("buildSrc/src/main/java/AnonymousAction.java") << javaClass("AnonymousAction", anonymousClassPrintingString("ACTION", "From Anonymous"))

        setupCustomTask()

        def script = """
            task myTask(type: CustomTask) {
                outputs.cacheIf { true }
            }
        """

        buildFile << script <<
            """
            myTask.doLast(
                providers.gradleProperty("anonymous").isPresent()
                    ? AnonymousAction.ACTION
                    : LambdaAction.ACTION
            )
        """

        when:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown {
            additionalTaskAction(':myTask')
            implementedByLambda('LambdaAction')
            includeLink()
        })
        withBuildCache().run "myTask"
        then:
        executedAndNotSkipped(":myTask")

        when:
        withBuildCache().run "myTask", "-Panonymous"
        then:
        executedAndNotSkipped(":myTask")

        when:
        withBuildCache().run "myTask", "-Panonymous"
        then:
        skipped(":myTask")

        when:
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown {
            additionalTaskAction(':myTask')
            implementedByLambda('LambdaAction')
            includeLink()
        })
        withBuildCache().run "myTask"
        then:
        executedAndNotSkipped(":myTask")

        when:
        withBuildCache().run "myTask", "-Panonymous"
        then:
        skipped(":myTask")
    }

    @ToBeImplemented
    def "serializable lambda can be used as task action"() {
        file("buildSrc/src/main/java/LambdaAction.java") << javaClass("LambdaAction", serializableLambdaPrintingString("ACTION", "From Lambda"))
        setupCustomTask()

        def script = """
            task myTask(type: CustomTask)
        """

        buildFile << script <<
            """
            myTask.doLast(LambdaAction.ACTION)
        """

        when:
        // There shouldn't be a deprecation message
        expectThatExecutionOptimizationDisabledWarningIsDisplayed(executer, implementationUnknown {
            additionalTaskAction(':myTask')
            implementedByLambda('LambdaAction')
            includeLink()
        })
        run "myTask"
        then:
        executedAndNotSkipped(":myTask")
//
//        when:
//        run "myTask"
//        then:
//        skipped(":myTask")
    }

    private TestFile setupCustomTask() {
        file("buildSrc/src/main/java/CustomTask.java") << """
                    import org.gradle.api.Action;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.NonNullApi;
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.tasks.OutputFile;
            import org.gradle.api.tasks.TaskAction;

            import java.io.File;
            import java.io.IOException;
            import java.nio.file.Files;

            @NonNullApi
            public class CustomTask extends DefaultTask {
                private File outputFile = new File(getTemporaryDir(), "output.txt");

                @OutputFile
                public File getOutputFile() {
                    return outputFile;
                }

                public void setOutputFile(File outputFile) {
                    this.outputFile = outputFile;
                }

                @TaskAction
                public void doStuff() {
                    try {
                        Files.write(outputFile.toPath(), "Some output".getBytes());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """
    }

    private static String javaClass(String className, String classBody) {
        """
            import org.gradle.api.Action;
            import org.gradle.api.Task;

            import java.io.File;
            import java.io.IOException;
            import java.nio.file.Files;

            public class ${className} {
${classBody}
            }
        """
    }

    private static String lambdaWritingFile(String constantName, String outputString) {
        """
                public static final Action<File> ${constantName} = file -> {
                    try {
                        Files.write(file.toPath(), "${outputString}".getBytes());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };
        """
    }

    private static String anonymousClassWritingFile(String constantName, String outputString) {
        """
                public static final Action<File> ${constantName} = new Action<File>() {
                    @Override
                    public void execute(File file) {
                        try {
                            Files.write(file.toPath(), "${outputString}".getBytes());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
        """
    }

    private static String lambdaPrintingString(String constantName, String outputString) {
        """
                public static final Action<Task> ${constantName} = task -> {
                    System.out.println("${outputString}");
                };
        """
    }

    private static String serializableLambdaPrintingString(String constantName, String outputString) {
        """
                public static final org.gradle.api.internal.lambdas.SerializableLambdas.SerializableAction<Task> ${constantName} = task -> {
                    System.out.println("${outputString}");
                };
        """
    }

    private static String anonymousClassPrintingString(String constantName, String outputString) {
        """
                public static final Action<Task> ${constantName} = new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        System.out.println("${outputString}");
                    }
                };
        """
    }
}
