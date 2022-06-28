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
import org.gradle.integtests.fixtures.ExecutionOptimizationDeprecationFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.reflect.problems.ValidationProblemId
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.internal.reflect.validation.ValidationTestFor
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class LambdaInputsIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker, DirectoryBuildCacheFixture, ExecutionOptimizationDeprecationFixture {

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
        outputContains("Implementation of input property 'action' has changed for task ':myTask'")

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

    @ToBeFixedForConfigurationCache(because = "https://github.com/gradle/gradle/issues/21109")
    @ValidationTestFor(
        ValidationProblemId.UNKNOWN_IMPLEMENTATION
    )
    @Issue("https://github.com/gradle/gradle/issues/5510")
    def "task with nested property defined by non-serializable Java lambda disables execution optimizations"() {
        setupTaskClassWithConsumerProperty()
        def originalClassName = "LambdaOriginal"
        def changedClassName = "LambdaChanged"

        file("buildSrc/src/main/java/${originalClassName}.java") <<
            javaClass(originalClassName, nonSerializableLambdaWritingFile("ACTION", "original"))
        file("buildSrc/src/main/java/${changedClassName}.java") <<
            javaClass(changedClassName, nonSerializableLambdaWritingFile("ACTION", "changed"))

        buildFile << """
            task myTask(type: TaskWithConsumerProperty) {
                consumer = providers.gradleProperty("changed").isPresent()
                    ? ${changedClassName}.ACTION
                    : ${originalClassName}.ACTION
            }
        """

        buildFile.makeOlder()

        when:
        expectImplementationUnknownDeprecation {
            nestedProperty('consumer')
            implementedByLambda(originalClassName)
        }
        run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        expectImplementationUnknownDeprecation {
            nestedProperty('consumer')
            implementedByLambda(originalClassName)
        }
        run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        expectImplementationUnknownDeprecation {
            nestedProperty('consumer')
            implementedByLambda(changedClassName)
        }
        run 'myTask', '-Pchanged'
        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "changed"
    }

    def "can change nested property from one serializable Java lambda to another and back"() {
        setupSerializableConsumerInterface()
        setupTaskClassWithConsumerProperty()
        def originalClassName = "LambdaOriginal"
        def changedClassName = "LambdaChanged"
        file("buildSrc/src/main/java/${originalClassName}.java") << javaClass(originalClassName, serializableConsumerLambdaWritingFile("ACTION", "original"))
        file("buildSrc/src/main/java/${changedClassName}.java") << javaClass(changedClassName, serializableConsumerLambdaWritingFile("ACTION", "changed"))
        buildFile << """
            task myTask(type: TaskWithConsumerProperty) {
                consumer = providers.gradleProperty("changed").isPresent()
                    ? ${changedClassName}.ACTION
                    : ${originalClassName}.ACTION
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
        run 'myTask', '-Pchanged', '--info'
        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "changed"
        outputContains("Implementation of input property 'consumer' has changed for task ':myTask'")
    }

    @Issue("https://github.com/gradle/gradle/issues/5510")
    def "can change nested property from one Action Java lambda to another and back"() {
        setupTaskClassWithActionProperty()
        def originalClassName = "LambdaOriginal"
        def changedClassName = "LambdaChanged"
        file("buildSrc/src/main/java/${originalClassName}.java") << javaClass(originalClassName, actionLambdaWritingFile("ACTION", "original"))
        file("buildSrc/src/main/java/${changedClassName}.java") << javaClass(changedClassName, actionLambdaWritingFile("ACTION", "changed"))
        buildFile << """
            task myTask(type: TaskWithActionProperty) {
                action = providers.gradleProperty("changed").isPresent()
                    ? ${changedClassName}.ACTION
                    : ${originalClassName}.ACTION
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
        run 'myTask', '-Pchanged', '--info'
        then:
        executedAndNotSkipped(':myTask')
        file('build/tmp/myTask/output.txt').text == "changed"
        outputContains("Implementation of input property 'action' has changed for task ':myTask'")
    }

    def "can change nested property from an Action Java lambda to anonymous inner class and back"() {
        setupTaskClassWithActionProperty()
        def lambdaClassName = "LambdaAction"
        def anonymousClassName = "AnonymousAction"
        file("buildSrc/src/main/java/${lambdaClassName}.java") << javaClass(lambdaClassName, actionLambdaWritingFile("ACTION", "lambda"))
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
        withBuildCache().run 'myTask'
        then:
        executedAndNotSkipped(':myTask')

        when:
        withBuildCache().run 'myTask', '-Panonymous'
        then:
        executedAndNotSkipped(':myTask')

        when:
        withBuildCache().run 'myTask', '-Panonymous'
        then:
        skipped(':myTask')

        when:
        withBuildCache().run 'myTask'
        then:
        skipped(':myTask')

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

    private TestFile setupTaskClassWithConsumerProperty() {
        file("buildSrc/src/main/java/TaskWithConsumerProperty.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.NonNullApi;
            import org.gradle.api.tasks.Nested;
            import org.gradle.api.tasks.OutputFile;
            import org.gradle.api.tasks.TaskAction;

            import java.io.File;
            import java.util.function.Consumer;

            @NonNullApi
            public class TaskWithConsumerProperty extends DefaultTask {
                private File outputFile = new File(getTemporaryDir(), "output.txt");
                private Consumer<File> consumer;

                @OutputFile
                public File getOutputFile() {
                    return outputFile;
                }

                public void setOutputFile(File outputFile) {
                    this.outputFile = outputFile;
                }

                @Nested
                public Consumer<File> getConsumer() {
                    return consumer;
                }

                public void setConsumer(Consumer<File> consumer) {
                    this.consumer = consumer;
                }

                @TaskAction
                public void doStuff() {
                    getConsumer().accept(outputFile);
                }
            }
        """
    }

    @Issue(["https://github.com/gradle/gradle/issues/5510", "https://github.com/gradle/gradle/issues/17327"])
    def "task with Java lambda actions detects changes"() {
        setupCustomTask()

        def originalClassName = "LambdaOriginal"
        def changedClassName = "LambdaChanged"
        file("buildSrc/src/main/java/${originalClassName}.java") << javaClass(originalClassName, lambdaPrintingString("ACTION", "original"))
        file("buildSrc/src/main/java/${changedClassName}.java") << javaClass(changedClassName, lambdaPrintingString("ACTION", "changed"))

        def script = """
            task myTask(type: CustomTask)
        """

        buildFile << script << """
            myTask.doLast(
                providers.gradleProperty("changed").isPresent()
                    ? ${changedClassName}.ACTION
                    : ${originalClassName}.ACTION
            )
        """

        when:
        run "myTask"
        then:
        executedAndNotSkipped(":myTask")

        when:
        run "myTask"
        then:
        skipped(":myTask")

        when:
        run "myTask", "-Pchanged", "--info"
        then:
        executedAndNotSkipped(":myTask")
        outputContains("One or more additional actions for task ':myTask' have changed.")
    }

    def "can change lambda action to anonymous inner class and back"() {
        file("buildSrc/src/main/java/LambdaAction.java") << javaClass("LambdaAction", lambdaPrintingString("ACTION", "From Lambda"))
        file("buildSrc/src/main/java/AnonymousAction.java") << javaClass("AnonymousAction", anonymousClassPrintingString("ACTION", "From Anonymous"))

        setupCustomTask()

        def script = """
            task myTask(type: CustomTask) {
                outputs.cacheIf { true }
            }
        """

        buildFile << script << """
            myTask.doLast(
                providers.gradleProperty("anonymous").isPresent()
                    ? AnonymousAction.ACTION
                    : LambdaAction.ACTION
            )
        """

        when:
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
        withBuildCache().run "myTask"
        then:
        skipped(":myTask")

        when:
        withBuildCache().run "myTask", "-Panonymous"
        then:
        skipped(":myTask")
    }

    def "serializable lambda can be used as task action"() {
        file("buildSrc/src/main/java/LambdaAction.java") << javaClass("LambdaAction", serializableLambdaPrintingString("ACTION", "From Lambda"))
        setupCustomTask()

        def script = """
            task myTask(type: CustomTask)
        """

        buildFile << script << """
            myTask.doLast(LambdaAction.ACTION)
        """

        when:
        run "myTask"
        then:
        executedAndNotSkipped(":myTask")

        when:
        run "myTask"
        then:
        skipped(":myTask")
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

    private TestFile setupSerializableConsumerInterface() {
        // declaring in top-level package, so that importing at usage site is not required.
        file("buildSrc/src/main/java/CustomSerializableConsumer.java") << """
            import java.util.function.Consumer;
            import java.io.Serializable;

            public interface CustomSerializableConsumer<T> extends Consumer<T>, Serializable {}
        """
    }

    private static String javaClass(String className, String classBody) {
        """
            import org.gradle.api.Action;
            import org.gradle.api.Task;

            import java.io.File;
            import java.io.IOException;
            import java.nio.file.Files;
            import java.util.function.Consumer;

            public class ${className} {
${classBody}
            }
        """
    }

    /**
     * Configuration cache infrastructure makes sure that {@code Action} instances are always serializable.
     */
    private static String actionLambdaWritingFile(String constantName, String outputString) {
        lambdaWritingFile("Action", constantName, outputString)
    }

    /**
     * @see #setupSerializableConsumerInterface()
     */
    private static String serializableConsumerLambdaWritingFile(String constantName, String outputString) {
        lambdaWritingFile("CustomSerializableConsumer", constantName, outputString)
    }

    private static String nonSerializableLambdaWritingFile(String constantName, String outputString) {
        lambdaWritingFile("Consumer", constantName, outputString)
    }

    private static String lambdaWritingFile(String functionalInterface, String constantName, String outputString) {
        """
                public static final ${functionalInterface}<File> ${constantName} = file -> {
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
