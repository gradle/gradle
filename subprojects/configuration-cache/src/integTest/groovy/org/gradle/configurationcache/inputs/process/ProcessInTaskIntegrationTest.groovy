/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache.inputs.process

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.exec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.javaexec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.processBuilder
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.runtimeExec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.stringArrayExecute

class ProcessInTaskIntegrationTest extends AbstractProcessIntegrationTest {
    def "using #snippetsFactory.summary in task configuration is a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file("buildSrc/src/main/java/SneakyTask.java") << """
            import ${DefaultTask.name};
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${TaskAction.name};
            ${snippets.imports}

            public abstract class SneakyTask extends DefaultTask {
                @Inject
                protected abstract ExecOperations getExecOperations();

                public SneakyTask() {
                    ${snippets.body}
                }

                @TaskAction
                public void doNothing() {}
            }
        """

        buildFile("""
            tasks.register("sneakyTask", SneakyTask) {}
        """)

        when:
        configurationCacheFails(":sneakyTask")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("$location: external process started")
        }

        where:
        snippetsFactory                      | location
        exec("getProject()").java            | "Unknown location" // TODO(mlopatkin): Fix location there
        javaexec("getProject()").java        | "Unknown location"
        exec("getExecOperations()").java     | "Unknown location"
        javaexec("getExecOperations()").java | "Unknown location"
        processBuilder().java                | "Class `SneakyTask`"
        stringArrayExecute().java            | "Class `SneakyTask`"
        runtimeExec().java                   | "Class `SneakyTask`"
    }

    def "using #snippetsFactory.summary in task action is not a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file("buildSrc/src/main/java/SneakyTask.java") << """
            import ${DefaultTask.name};
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${TaskAction.name};
            ${snippets.imports}

            public abstract class SneakyTask extends DefaultTask {
                @Inject
                protected abstract ExecOperations getExecOperations();

                @TaskAction
                public void exec() {
                    ${snippets.body}
                }
            }
        """

        buildFile("""
            tasks.register("sneakyTask", SneakyTask) {}
        """)

        when:
        configurationCacheRun(":sneakyTask")

        then:
        outputContains("Hello")

        where:
        snippetsFactory                      | _
        exec("getExecOperations()").java     | _
        javaexec("getExecOperations()").java | _
        processBuilder().java                | _
        stringArrayExecute().java            | _
        runtimeExec().java                   | _
    }

    def "using #snippetsFactory.summary in task action of buildSrc is not a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file("buildSrc/build.gradle") << """
            import ${DefaultTask.name};
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${TaskAction.name};
            ${snippets.imports}

            public abstract class SneakyTask extends DefaultTask {
                @Inject
                protected abstract ExecOperations getExecOperations();

                @TaskAction
                public void exec() {
                    ${snippets.body}
                }
            }

            def sneakyTask = tasks.register("sneakyTask", SneakyTask) {}

            // Ensure that buildSrc compilation triggers an exec task.
            tasks.named("classes").configure {
                dependsOn(sneakyTask)
            }
        """

        when:
        configurationCacheRun(":help")

        then:
        outputContains("Hello")

        where:
        snippetsFactory                        | _
        exec("getExecOperations()").groovy     | _
        javaexec("getExecOperations()").groovy | _
        processBuilder().groovy                | _
        stringArrayExecute().groovy            | _
        runtimeExec().groovy                   | _
    }

    def "using #snippetsFactory.summary in worker task action of buildSrc is not a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file("buildSrc/build.gradle") << """
            import ${DefaultTask.name}
            import ${ExecOperations.name}
            import ${Inject.name}
            import ${TaskAction.name}
            import ${WorkAction.name}
            import ${WorkParameters.name}
            import ${WorkQueue.name}
            import ${WorkerExecutor.name}
            ${snippets.imports}

            public abstract class SneakyAction implements WorkAction<WorkParameters.None> {
                @Inject
                protected abstract ExecOperations getExecOperations();

                @Override
                void execute() {
                    ${snippets.body}
                }
            }

            public abstract class SneakyTask extends DefaultTask {

                @Inject
                abstract public WorkerExecutor getWorkerExecutor();

                @TaskAction
                public void exec() {
                    WorkQueue workQueue = getWorkerExecutor().noIsolation();
                    workQueue.submit(SneakyAction.class, parameters -> {})
                }
            }

            def sneakyTask = tasks.register("sneakyTask", SneakyTask) {}

            // Ensure that buildSrc compilation triggers an exec task.
            tasks.named("classes").configure {
                dependsOn(sneakyTask)
            }
        """

        when:
        configurationCacheRun(":help")

        then:
        outputContains("Hello")

        where:
        snippetsFactory                        | _
        exec("getExecOperations()").groovy     | _
        javaexec("getExecOperations()").groovy | _
        processBuilder().groovy                | _
        stringArrayExecute().groovy            | _
        runtimeExec().groovy                   | _
    }

    def "using #snippetsFactory.summary in task of included plugin build is not a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        def includedBuildFile = testDirectory.file("included/build.gradle")
        includedBuildFile << """
            import ${DefaultTask.name};
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${TaskAction.name};
            ${snippets.imports}

            plugins {
                id("groovy-gradle-plugin")
            }

            public abstract class SneakyTask extends DefaultTask {
                @Inject
                protected abstract ExecOperations getExecOperations();

                @TaskAction
                public void exec() {
                    ${snippets.body}
                }
            }

            def sneakyTask = tasks.register("sneakyTask", SneakyTask) {}

            // Ensure that plugin compilation triggers an exec task.
            tasks.named("classes").configure {
                dependsOn(sneakyTask)
            }
        """
        testDirectory.file("included/src/main/groovy/test-convention-plugin.gradle") << """
            println("Applied script plugin")
        """

        settingsFile("""
            pluginManagement {
                includeBuild('included')
            }
        """)

        buildFile("""
            plugins {
                id("test-convention-plugin")
            }
        """)

        when:
        configurationCacheRun(":help")

        then:
        outputContains("Hello")

        where:
        snippetsFactory                        | _
        exec("getExecOperations()").groovy     | _
        javaexec("getExecOperations()").groovy | _
        processBuilder().groovy                | _
        stringArrayExecute().groovy            | _
        runtimeExec().groovy                   | _
    }

    def "using #snippetsFactory.summary in task up-to-date is not a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file("buildSrc/src/main/java/SneakyTask.java") << """
            import ${DefaultTask.name};
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${TaskAction.name};
            ${snippets.imports}

            public abstract class SneakyTask extends DefaultTask {
                @Inject
                protected abstract ExecOperations getExecOperations();

                public SneakyTask() {
                  getOutputs().upToDateWhen(t -> {
                    ${snippets.body};
                    return false;
                  });
                }

                @TaskAction
                public void doNothing() {}
            }
        """

        buildFile("""
            tasks.register("sneakyTask", SneakyTask) {}
        """)

        when:
        configurationCacheRun(":sneakyTask")

        then:
        outputContains("Hello")

        where:
        snippetsFactory                      | _
        exec("getExecOperations()").java     | _
        javaexec("getExecOperations()").java | _
        processBuilder().java                | _
        stringArrayExecute().java            | _
        runtimeExec().java                   | _
    }

    def "using #snippetsFactory.summary in up-to-date task of buildSrc is not a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file("buildSrc/build.gradle") << """
            import ${DefaultTask.name};
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${TaskAction.name};
            ${snippets.imports}

            public abstract class SneakyTask extends DefaultTask {
                @Inject
                protected abstract ExecOperations getExecOperations();

                public SneakyTask() {
                  outputs.upToDateWhen {
                    ${snippets.body};
                    return false;
                  }
                }

                @TaskAction
                public void doNothing() {}
            }

            def sneakyTask = tasks.register("sneakyTask", SneakyTask) {}

            // Ensure that buildSrc compilation triggers an exec task.
            tasks.named("classes").configure {
                dependsOn(sneakyTask)
            }
        """

        when:
        configurationCacheRun(":help")

        then:
        outputContains("Hello")

        where:
        snippetsFactory                        | _
        exec("getExecOperations()").groovy     | _
        javaexec("getExecOperations()").groovy | _
        processBuilder().groovy                | _
        stringArrayExecute().groovy            | _
        runtimeExec().groovy                   | _
    }
}
