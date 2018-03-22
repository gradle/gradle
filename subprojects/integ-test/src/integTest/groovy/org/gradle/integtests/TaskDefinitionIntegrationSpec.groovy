/*
 * Copyright 2013 the original author or authors.
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
import spock.lang.Unroll

class TaskDefinitionIntegrationSpec extends AbstractIntegrationSpec {

    def "unsupported task parameter fails with decent error message"() {
        buildFile << "task a(Type:Copy)"
        when:
        fails 'a'
        then:
        failure.assertHasCause("Could not create task 'a': Unknown argument(s) in task definition: [Type]")
    }

    def "renders deprecation message when using left shift operator to define action"() {
        given:
        String taskName = 'helloWorld'
        String message = 'Hello world!'

        buildFile << """
            task $taskName << {
                println '$message'
            }
        """

        when:
        executer.expectDeprecationWarning()
        succeeds taskName

        then:
        output.contains(message)
        output.contains("The Task.leftShift(Closure) method has been deprecated and is scheduled to be removed in Gradle 5.0. Please use Task.doLast(Action) instead.")
    }

    def "can construct a custom task without constructor arguments"() {
        given:
        buildFile << """
            class CustomTask extends DefaultTask {
                @TaskAction
                void printIt() {
                    println("hello world")
                }
            }

            task myTask(type: CustomTask)
        """

        when:
        run 'myTask'

        then:
        result.output.contains('hello world')
    }

    @Unroll
    def "can construct a custom task with constructor arguments as #description via Map"() {
        given:
        buildFile << """
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                final String message
                final int number

                @Inject
                CustomTask(String message, int number) {
                    this.message = message
                    this.number = number
                }

                @TaskAction
                void printIt() {
                    println("\$message \$number")
                }
            }

            task myTask(type: CustomTask, constructorArgs: ${constructorArgs})
        """

        when:
        run 'myTask'

        then:
        result.output.contains("hello 42")

        where:
        description | constructorArgs
        'List'      | "['hello', 42]"
        'Object[]'  | "['hello', 42] as Object[]"
    }

    def "can construct a custom task with constructor arguments via API"() {
        given:
        buildFile << """
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                final String message
                final int number

                @Inject
                CustomTask(String message, int number) {
                    this.message = message
                    this.number = number
                }

                @TaskAction
                void printIt() {
                    println("\$message \$number")
                }
            }

            tasks.create('myTask', CustomTask, 'hello', 42)
        """

        when:
        run 'myTask'

        then:
        result.output.contains("hello 42")
    }

    def "fails to build custom task if constructor arguments missing"() {
        given:
        buildFile << """
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                final String message
                final int number

                @Inject
                CustomTask(String message, int number) {
                    this.message = message
                    this.number = number
                }

                @TaskAction
                void printIt() {
                    println("\$message \$number")
                }
            }

            task myTask(type: CustomTask, constructorArgs: ['hello'])
        """

        when:
        fails 'myTask'

        then:
        result.output.contains("org.gradle.internal.service.UnknownServiceException: No service of type int available")
    }

    @Unroll
    def "fails when constructorArgs not list or Object[], but #description"() {
        given:
        buildFile << """
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                final String message
                final int number

                @Inject
                CustomTask(String message, int number) {
                    this.message = message
                    this.number = number
                }

                @TaskAction
                void printIt() {
                    println("\$message \$number")
                }
            }

            task myTask(type: CustomTask, constructorArgs: ${constructorArgs})
        """

        when:
        fails 'myTask'

        then:
        result.output.contains("constructorArgs must be a List or Object[]")

        where:
        description | constructorArgs
        'Set'       | '[1, 2, 1] as Set'
        'Map'       | '[a: 1, b: 2]'
        'String'    | '"abc"'
        'primitive' | '123'
    }

    def "can construct a task with @Inject services"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                private final WorkerExecutor executor

                @Inject
                CustomTask(WorkerExecutor executor) {
                    this.executor = executor
                }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it" : "NOT IT")
                }
            }

            task myTask(type: CustomTask)
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it")
    }

    @Unroll
    def "can construct a task with @Inject services and constructor args as #description"() {
        given:
        buildFile << """
            import org.gradle.workers.WorkerExecutor
            import javax.inject.Inject

            class CustomTask extends DefaultTask {
                private final int number
                private final WorkerExecutor executor

                @Inject
                CustomTask(int number, WorkerExecutor executor) {
                    this.number = number
                    this.executor = executor
                }

                @TaskAction
                void printIt() {
                    println(executor != null ? "got it \$number" : "\$number NOT IT")
                }
            }

            task myTask(type: CustomTask, constructorArgs: ${constructorArgs})
        """

        when:
        run 'myTask'

        then:
        result.output.contains("got it 15")

        where:
        description | constructorArgs
        'List'      | '[ 15 ]'
        'Object[]'  | '[ 15 ] as Object[]'
    }

    def "can construct a custom task without constructor arguments from plugin"() {
        given:
        file('buildSrc/src/main/java/CustomPlugin.java') << '''
import org.gradle.api.*;
import org.gradle.api.tasks.*;

public class CustomPlugin implements Plugin<Project> {
    public static class CustomTask extends DefaultTask {
        @TaskAction
        void printIt() {
           System.out.println("hello world");
        }
    }

    @Override
    public void apply(Project p) {
        p.getTasks().create("myTask", CustomTask.class);
    }
}
'''

        file('buildSrc/src/main/resources/META-INF/gradle-plugins/custom.properties') << 'implementation-class=CustomPlugin'

        buildFile << "apply plugin: 'custom'"

        when:
        run 'myTask'

        then:
        result.output.contains('hello world')
    }

    def "can construct a custom task with constructor arguments via API from a plugin"() {
        given:
        file('buildSrc/src/main/java/CustomPlugin.java') << '''
import org.gradle.api.*;
import org.gradle.api.tasks.*;
import javax.inject.Inject;

public class CustomPlugin implements Plugin<Project> {
    public static class CustomTask extends DefaultTask {
        private final String message;
        private final int number;

        @Inject
        public CustomTask(String message, int number) {
            this.message = message;
            this.number = number;
        }

        @TaskAction
        void printIt() {
            System.out.println(message + " " + number);
        }
    }

    @Override
    public void apply(Project p) {
        p.getTasks().create("myTask", CustomTask.class, "hello", 42);
    }
}
'''

        file('buildSrc/src/main/resources/META-INF/gradle-plugins/custom.properties') << 'implementation-class=CustomPlugin'

        buildFile << "apply plugin: 'custom'"

        when:
        run 'myTask'

        then:
        result.output.contains("hello 42")
    }

    def "fails to build custom task if constructor arguments missing from a plugin"() {
        given:
        file('buildSrc/src/main/java/CustomPlugin.java') << '''
import org.gradle.api.*;
import org.gradle.api.tasks.*;
import javax.inject.Inject;

public class CustomPlugin implements Plugin<Project> {
    public static class CustomTask extends DefaultTask {
        private final String message;
        private final int number;

        @Inject
        public CustomTask(String message, int number) {
            this.message = message;
            this.number = number;
        }

        @TaskAction
        void printIt() {
            System.out.println(message + " " + number);
        }
    }

    @Override
    public void apply(Project p) {
        p.getTasks().create("myTask", CustomTask.class, "hello");
    }
}
'''

        file('buildSrc/src/main/resources/META-INF/gradle-plugins/custom.properties') << 'implementation-class=CustomPlugin'

        buildFile << "apply plugin: 'custom'"

        when:
        fails 'myTask'

        then:
        result.output.contains("org.gradle.internal.service.UnknownServiceException: No service of type int available")
    }

    def "can construct a task with @Inject services from a plugin"() {
        given:
        file('buildSrc/src/main/java/CustomPlugin.java') << '''
import org.gradle.api.*;
import org.gradle.api.tasks.*;
import org.gradle.workers.*;
import javax.inject.Inject;

public class CustomPlugin implements Plugin<Project> {
    public static class CustomTask extends DefaultTask {
        private final WorkerExecutor executor;

        @Inject
        public CustomTask(WorkerExecutor executor) {
            this.executor = executor;
        }

        @TaskAction
        void printIt() {
            System.out.println(executor != null ? "got it" : "NOT IT");
        }
    }

    @Override
    public void apply(Project p) {
        p.getTasks().create("myTask", CustomTask.class);
    }
}
'''

        file('buildSrc/src/main/resources/META-INF/gradle-plugins/custom.properties') << 'implementation-class=CustomPlugin'

        buildFile << "apply plugin: 'custom'"

        when:
        run 'myTask'

        then:
        result.output.contains("got it")
    }

    def "can construct a task with @Inject services and constructor args from a plugin"() {
        given:
        file('buildSrc/src/main/java/CustomPlugin.java') << '''
import org.gradle.api.*;
import org.gradle.api.tasks.*;
import org.gradle.workers.*;
import javax.inject.Inject;

public class CustomPlugin implements Plugin<Project> {
    public static class CustomTask extends DefaultTask {
        private final int number;
        private final WorkerExecutor executor;

        @Inject
        public CustomTask(int number, WorkerExecutor executor) {
            this.number = number;
            this.executor = executor;
        }

        @TaskAction
        void printIt() {
            if (executor != null) {
                System.out.println("got it " + number);
            } else {
                System.out.println(number + " NOT IT");
            }
        }
    }

    @Override
    public void apply(Project p) {
        p.getTasks().create("myTask", CustomTask.class, 15);
    }
}
'''

        file('buildSrc/src/main/resources/META-INF/gradle-plugins/custom.properties') << 'implementation-class=CustomPlugin'

        buildFile << "apply plugin: 'custom'"

        when:
        run 'myTask'

        then:
        result.output.contains("got it 15")
    }
}
