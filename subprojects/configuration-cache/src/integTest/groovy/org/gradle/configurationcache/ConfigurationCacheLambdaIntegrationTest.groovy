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

class ConfigurationCacheLambdaIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "restores task fields whose value is a serializable #kind Java lambda"() {
        given:
        file("buildSrc/src/main/java/my/LambdaTask.java").tap {
            parentFile.mkdirs()
            text = """
                package my;

                import org.gradle.api.*;
                import org.gradle.api.tasks.*;

                public class LambdaTask extends DefaultTask {

                    public interface SerializableSupplier<T> extends java.io.Serializable {
                        T get();
                    }

                    private SerializableSupplier<Integer> supplier;

                    public void setSupplier(SerializableSupplier<Integer> supplier) {
                        this.supplier = supplier;
                    }

                    public void setNonInstanceCapturingLambda() {
                        final int i = getName().length();
                        setSupplier(() -> i);
                    }

                    public void setInstanceCapturingLambda() {
                        setSupplier(() -> getName().length());
                    }

                    @TaskAction
                    void printValue() {
                        System.out.println("this.supplier.get() -> " + this.supplier.get());
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
        outputContains("this.supplier.get() -> 2")

        where:
        kind                     | expression
        "instance capturing"     | "setInstanceCapturingLambda()"
        "non-instance capturing" | "setNonInstanceCapturingLambda()"
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
}
