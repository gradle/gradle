/*
 * Copyright 2019 the original author or authors.
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
import spock.lang.Issue

class TaskPropertiesIntegrationTest extends AbstractIntegrationSpec {
    def "can define task with abstract read-only Property<T> property"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract Property<Integer> getCount()

                @TaskAction
                void go() {
                    println("count = \${count.get()}")
                }
            }

            tasks.create("thing", MyTask) {
                println("property = \$count")
                count = 12
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("property = task ':thing' property 'count'")
        outputContains("count = 12")
    }

    def "reports failure to query managed Property<T> with no value"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract Property<Integer> getCount()

                @TaskAction
                void go() {
                    println("count = \${count.get()}")
                }
            }

            tasks.create("thing", MyTask) {
            }
        """

        when:
        fails("thing")

        then:
        failure.assertHasCause("Cannot query the value of task ':thing' property 'count' because it has no value available.")
    }

    def "reports failure to query read-only unmanaged Property<T> with final getter"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Internal
                final Property<Integer> count = project.objects.property(Integer)

                @TaskAction
                void go() {
                    println("count = \${count.get()}")
                }
            }

            tasks.create("thing", MyTask) {
                println("property = \$count")
            }
        """

        when:
        fails("thing")

        then:
        outputContains("property = task ':thing' property 'count'")
        failure.assertHasCause("Cannot query the value of task ':thing' property 'count' because it has no value available.")
    }

    def "reports failure to query read-only unmanaged Property<T>"() {
        given:
        file("buildSrc/src/main/java/MyTask.java") << """
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;

            public abstract class MyTask extends DefaultTask {
                private final Property<Integer> count = getProject().getObjects().property(Integer.class);

                @Internal
                public Property<Integer> getCount() {
                    return count;
                }

                @TaskAction
                void go() {
                    System.out.println("count = " + count.get());
                }
            }
        """

        buildFile << """
            tasks.create("thing", MyTask) {
                println("property = \$count")
            }
        """

        when:
        fails("thing")

        then:
        outputContains("property = task ':thing' property 'count'")
        failure.assertHasCause("Cannot query the value of task ':thing' property 'count' because it has no value available.")
    }

    def "can define task with abstract read-only ConfigurableFileCollection property"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileCollection getSource()

                @TaskAction
                void go() {
                    println("files = \${source.files.name}")
                }
            }

            tasks.create("thing", MyTask) {
                source.from("a", "b", "c")
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("files = [a, b, c]")
    }

    def "can define task with abstract read-only ConfigurableFileTree property"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @InputFiles
                abstract ConfigurableFileTree getSource()

                @TaskAction
                void go() {
                    println("files = \${source.files.name.sort()}")
                }
            }

            tasks.create("thing", MyTask) {
                source.from("dir")
            }
        """
        file("dir/sub/a.txt").createFile()
        file("dir/b.txt").createFile()

        when:
        succeeds("thing")

        then:
        outputContains("files = [a.txt, b.txt]")
    }

    def "can define task with abstract read-only NamedDomainObjectContainer<T> property"() {
        given:
        buildFile << """
            abstract class Bean {
                @Internal
                final String name
                @Input
                abstract Property<String> getProp()

                Bean(String name) {
                    this.name = name
                }
            }

            abstract class MyTask extends DefaultTask {
                @Nested
                abstract NamedDomainObjectContainer<Bean> getBeans()

                @TaskAction
                void go() {
                    println("beans = \${beans.collect { it.prop.get() } }")
                }
            }

            tasks.create("thing", MyTask) {
                beans {
                    one { prop = '1' }
                    two { prop = '2' }
                }
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("beans = [1, 2]")
    }

    def "can define task with abstract read-only DomainObjectSet<T> property"() {
        given:
        buildFile << """
            class Bean {
                @Input
                String prop
            }

            abstract class MyTask extends DefaultTask {
                @Nested
                abstract DomainObjectSet<Bean> getBeans()

                @TaskAction
                void go() {
                    println("beans = \${beans.collect { it.prop } }")
                }
            }

            tasks.create("thing", MyTask) {
                beans.add(new Bean(prop: '1'))
                beans.add(new Bean(prop: '2'))
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("beans = [1, 2]")
    }

    def "can define task with abstract read-only @Nested property"() {
        given:
        buildFile << """
            interface Params {
                @Input
                Property<Integer> getCount()
            }
            abstract class MyTask extends DefaultTask {
                @Nested
                abstract Params getParams()

                @TaskAction
                void go() {
                    println("count = \${params.count.get()}")
                }
            }

            tasks.create("thing", MyTask) {
                println("params = \$params")
                println("params.count = \$params.count")
                params.count = 12
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("params = task ':thing' property 'params'")
        outputContains("params.count = task ':thing' property 'params.count'")
        outputContains("count = 12")
    }

    def "can define task with non-abstract read-only @Nested property"() {
        given:
        buildFile << """
            interface Params {
                @Input
                Property<Integer> getCount()
            }
            class MyTask extends DefaultTask {
                private final params = project.objects.newInstance(Params)

                @Nested
                Params getParams() { return params }

                @TaskAction
                void go() {
                    println("count = \${params.count.get()}")
                }
            }

            tasks.create("thing", MyTask) {
                println("params = \$params")
                println("params.count = \$params.count")
                params.count = 12
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("params = task ':thing' property 'params'")
        outputContains("params.count = task ':thing' property 'params.count'")
        outputContains("count = 12")
    }

    def "can query generated read only property in constructor"() {
        given:
        buildFile << """
            abstract class MyTask extends DefaultTask {
                @Internal
                abstract Property<String> getParam()

                MyTask() {
                    param.convention("from convention")
                }

                @TaskAction
                void go() {
                    println("param = \${param.get()}")
                }
            }

            tasks.create("thing", MyTask) {
                param.set("from configuration")
            }
        """

        when:
        succeeds("thing")

        then:
        outputContains("param = from configuration")
    }

    def "cannot modify task's input properties via returned map"() {
        given:
        buildFile << """
            tasks.create("thing") {
                inputs.properties.put("Won't", "happen")
            }
        """

        when:
        fails("thing")

        then:
        errorOutput.contains("java.lang.UnsupportedOperationException")
    }

    @Issue("https://github.com/gradle/gradle/issues/12133")
    def "abstract super type can define concrete property"() {
        // Problem is exposed by Java compiler
        file("buildSrc/src/main/java/AbstractCustomTask.java") << """
            import org.gradle.api.file.ConfigurableFileCollection;
            import org.gradle.api.DefaultTask;
            import org.gradle.api.tasks.InputFiles;

            abstract class AbstractCustomTask extends DefaultTask {
                private final ConfigurableFileCollection sourceFiles = getProject().files();

                @InputFiles
                public ConfigurableFileCollection getSourceFiles() {
                    System.out.println("get files from field");
                    return sourceFiles;
                }

                public void setSourceFiles(Object files) {
                    System.out.println("set files using field");
                    sourceFiles.setFrom(files);
                }
            }
        """
        file("buildSrc/src/main/java/CustomTask.java") << """
            import org.gradle.api.GradleException;
            import org.gradle.api.tasks.TaskAction;

            public class CustomTask extends AbstractCustomTask {
                @TaskAction
                public void checkFiles() {
                    System.out.println("checking files");
                    if (getSourceFiles().isEmpty()) {
                        throw new GradleException("sourceFiles are unexpectedly empty");
                    }
                    System.out.println("done checking files");
                }
            }
        """

        buildFile << """
            task check(type: CustomTask) {
                check.setSourceFiles("in.txt")
            }
        """

        expect:
        succeeds("check")
    }
}
