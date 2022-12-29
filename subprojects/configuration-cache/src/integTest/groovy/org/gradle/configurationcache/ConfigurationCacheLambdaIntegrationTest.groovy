/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.SourceDirectorySet
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture

class ConfigurationCacheLambdaIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def fixture = new ConfigurationCacheFixture(this)

    def "restores task fields whose value is a #kind Java lambda"() {
        given:
        file("buildSrc/src/main/java/my/LambdaTask.java").tap {
            parentFile.mkdirs()
            text = """
                package my;

                import org.gradle.api.*;
                import org.gradle.api.tasks.*;

                public class LambdaTask extends DefaultTask {

                    // Test with serializable lambdas that should work as-is, as well as non-serializable lambdas which should
                    // be forced to become serializable by the instrumentation:
                    public interface NonSerializableSupplier<T> {
                        T get();
                    }
                    public interface SerializableSupplier<T> extends java.io.Serializable {
                        T get();
                    }

                    private SerializableSupplier<Integer> serializableSupplier;
                    private NonSerializableSupplier<Integer> nonSerializableSupplier;

                    public void setSerializableSupplier(SerializableSupplier<Integer> supplier) {
                        this.serializableSupplier = supplier;
                    }

                    public void setNonSerializableSupplier(NonSerializableSupplier<Integer> supplier) {
                        this.nonSerializableSupplier = supplier;
                    }

                    public void setNonInstanceCapturingLambda() {
                        final int i = getName().length();
                        setSerializableSupplier(() -> i);
                        setNonSerializableSupplier(() -> i);
                    }

                    public void setInstanceCapturingLambda() {
                        setSerializableSupplier(() -> getName().length());
                        setNonSerializableSupplier(() -> getName().length());
                    }

                    @TaskAction
                    void printValue() {
                        System.out.println("this.serializableSupplier.get() -> " + this.serializableSupplier.get());
                        System.out.println("this.nonSerializableSupplier.get() -> " + this.nonSerializableSupplier.get());
                    }
                }
            """
        }

        buildFile << """
            task ok(type: my.LambdaTask) {
                $expression
            }
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("this.serializableSupplier.get() -> 2\nthis.nonSerializableSupplier.get() -> 2")

        where:
        kind                     | expression
        "instance capturing"     | "setInstanceCapturingLambda()"
        "non-instance capturing" | "setNonInstanceCapturingLambda()"
    }

    def "capturing prohibited types in serializable lambdas is reported as a problem"() {
        given:
        file("buildSrc/src/main/java/my/LambdaTask.java").tap {
            parentFile.mkdirs()
            text = """
                package my;

                import java.util.*;
                import java.util.function.Supplier;
                import org.gradle.api.*;
                import org.gradle.api.tasks.*;
                import org.gradle.api.artifacts.Configuration;
                import org.gradle.api.file.SourceDirectorySet;

                public class LambdaTask extends DefaultTask {
                    private List<Supplier<String>> suppliers = new ArrayList<>();

                    public void addSupplier(Supplier<String> supplier) {
                        suppliers.add(supplier);
                    }

                    public void addSupplierWithConfiguration() {
                        Configuration c = getProject().getConfigurations().create("test");
                        addSupplier(() -> "configuration name is " + c.getName());
                    }

                    public void addSupplierWithSourceDirectorySet() {
                        SourceDirectorySet s = getProject().getObjects().sourceDirectorySet("test", "test");
                        addSupplier(() -> "source directory set name is " + s.getName());
                    }

                    @TaskAction
                    void printValue() {
                        for (Supplier<String> supplier : suppliers) {
                            System.out.println("supplier -> " + supplier.get());
                        }
                    }
                }
            """
        }

        buildFile << """
            task ok(type: my.LambdaTask) {
                addSupplierWithConfiguration()
                addSupplierWithSourceDirectorySet()
            }
        """

        when:
        configurationCacheFails("ok")

        then:
        fixture.assertStateStoredAndDiscarded {
            [Configuration.class, SourceDirectorySet.class].each {
                serializationProblem(
                    "Task `:ok` of type `my.LambdaTask`: cannot serialize a lambda that captures or accepts a parameter of type '" +
                        it.name +
                        "' as these are not supported with the configuration cache"
                )
            }
        }
        outputContains("supplier -> configuration name is test")
        outputContains("supplier -> source directory set name is test")
    }


    def "restores task with action and spec that are Java lambdas"() {
        given:
        file("buildSrc/src/main/java/my/LambdaPlugin.java").tap {
            parentFile.mkdirs()
            text = """
                package my;

                import org.gradle.api.*;
                import org.gradle.api.tasks.*;

                public class LambdaPlugin implements Plugin<Project> {
                    public void apply(Project project) {
                        $type value = $expression;
                        project.getTasks().register("ok", task -> {
                            task.doLast(t -> {
                                System.out.println(task.getName() + " action value is " + value);
                            });
                            task.onlyIf(t -> {
                                System.out.println(task.getName() + " spec value is " + value);
                                return true;
                            });
                        });
                    }
                }
            """
        }

        buildFile << """
            apply plugin: my.LambdaPlugin
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("ok action value is ${value}")
        outputContains("ok spec value is ${value}")

        where:
        type      | expression | value
        "String"  | '"value"'  | "value"
        "int"     | "12"       | "12"
        "boolean" | "true"     | "true"
    }

    def "restores task with Transformer implemented by Java lambda"() {
        given:
        file("buildSrc/src/main/java/my/LambdaPlugin.java").tap {
            parentFile.mkdirs()
            text = """
                package my;

                import org.gradle.api.*;
                import org.gradle.api.tasks.*;

                public class LambdaPlugin implements Plugin<Project> {
                    public void apply(Project project) {
                        project.getTasks().register("ok", task -> {
                            Transformer<String, String> tx = String::toUpperCase;
                            task.doLast(t -> {
                                System.out.println(tx.transform(task.getName()) + "!");
                            });
                        });
                    }
                }
            """
        }

        buildFile << """
            apply plugin: my.LambdaPlugin
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("OK!")
    }

    def "restores task with CommandLineArgumentProvider implemented by Java lambda"() {
        given:
        file("buildSrc/src/main/java/my/LambdaPlugin.java").tap {
            parentFile.mkdirs()
            text = """
                package my;

                import org.gradle.api.*;
                import org.gradle.api.tasks.*;
                import org.gradle.process.CommandLineArgumentProvider;

                import java.util.Collections;

                public class LambdaPlugin implements Plugin<Project> {
                    public void apply(Project project) {
                        project.getTasks().register("ok", task -> {
                            CommandLineArgumentProvider cmdLineArgumentProvider = () -> Collections.singleton("-Dfoo=bar");
                            task.doLast(t -> {
                                System.out.println("args: " + cmdLineArgumentProvider.asArguments());
                            });
                        });
                    }
                }
            """
        }

        buildFile << """
            apply plugin: my.LambdaPlugin
        """

        when:
        configurationCacheRun "ok"
        configurationCacheRun "ok"

        then:
        outputContains("args: [-Dfoo=bar]")
    }

    def "lambda serialization can handle implementation methods with the same name"() {
        given:
        file("buildSrc/src/main/java/my/LambdaPlugin.java").tap {
            parentFile.mkdirs()
            text = """
                package my;

                import org.gradle.api.*;

                import javax.inject.Inject;

                class TaskA extends DefaultTask {
                    @Inject
                    public TaskA() { }
                }

                class TaskB extends DefaultTask {
                    @Inject
                    public TaskB() { }
                }

                public class LambdaPlugin implements Plugin<Project> {
                    // Use these overloads as lambda implementation methods - they should appear in SerializedLambda
                    static void foo(TaskA taskA) { }
                    static void foo(TaskB taskB) { }

                    @Override
                    public void apply(Project project) {
                        Action<? super TaskA> actionA = LambdaPlugin::foo;
                        Action<? super TaskB> actionB = LambdaPlugin::foo;

                        project.getTasks().register("a", TaskA.class, task -> task.doLast(a -> actionA.execute((TaskA) a)));
                        project.getTasks().register("b", TaskB.class, task -> task.doLast(b -> actionB.execute((TaskB) b)));
                    }
                }
            """
        }
        buildFile << """
            apply plugin: my.LambdaPlugin
        """

        when:
        configurationCacheRun("a", "b")
        configurationCacheRun("a", "b")

        then:
        succeeds("a", "b")
    }
}
