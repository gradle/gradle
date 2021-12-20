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

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.api.tasks.TaskAction
import org.gradle.configurationcache.fixtures.ExecOperationsFixture
import org.gradle.configurationcache.fixtures.ExecOperationsFixture.SnippetsFactory
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

import static org.gradle.configurationcache.fixtures.ExecOperationsFixture.exec
import static org.gradle.configurationcache.fixtures.ExecOperationsFixture.javaexec

class ConfigurationCacheExternalProcessIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    ExecOperationsFixture execOperationsFixture = new ExecOperationsFixture(testDirectory)

    def "using #snippetsFactory.summary in #location.toLowerCase() #file is a problem"(SnippetsFactory snippetsFactory,
                                                                                       String file,
                                                                                       String location) {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file(file) << """
            ${snippets.imports}
            ${snippets.body}
        """

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("$location '${relativePath(file)}': external process started")
        }

        where:
        snippetsFactory   | file                           | location
        exec().groovy     | "build.gradle"                 | "Build file"
        javaexec().groovy | "build.gradle"                 | "Build file"
        exec().kotlin     | "build.gradle.kts"             | "Build file"
        javaexec().kotlin | "build.gradle.kts"             | "Build file"
        exec().groovy     | "settings.gradle"              | "Settings file"
        javaexec().groovy | "settings.gradle"              | "Settings file"
        exec().kotlin     | "settings.gradle.kts"          | "Settings file"
        javaexec().kotlin | "settings.gradle.kts"          | "Settings file"
        exec().groovy     | "buildSrc/build.gradle"        | "Build file"
        javaexec().groovy | "buildSrc/build.gradle"        | "Build file"
        exec().kotlin     | "buildSrc/build.gradle.kts"    | "Build file"
        javaexec().kotlin | "buildSrc/build.gradle.kts"    | "Build file"
        exec().groovy     | "buildSrc/settings.gradle"     | "Settings file"
        javaexec().groovy | "buildSrc/settings.gradle"     | "Settings file"
        exec().kotlin     | "buildSrc/settings.gradle.kts" | "Settings file"
        javaexec().kotlin | "buildSrc/settings.gradle.kts" | "Settings file"
    }

    def "using #snippetsFactory.summary in initialization script #file is a problem"(SnippetsFactory snippetsFactory, String file) {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)

        def initScriptFile = testDirectory.file(file)
        initScriptFile << """
            ${snippets.imports}
            ${snippets.body}
        """
        executer.usingInitScript(initScriptFile)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("Initialization script '${relativePath(file)}': external process started")
        }

        where:
        snippetsFactory   | file
        exec().groovy     | "exec.init.gradle"
        javaexec().groovy | "exec.init.gradle"
        exec().kotlin     | "exec.init.gradle.kts"
        javaexec().kotlin | "exec.init.gradle.kts"
    }

    def "using #snippetsFactory.summary in included plugin settings #file is a problem"(SnippetsFactory snippetsFactory,
                                                                                        String file) {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file(file) << """
            ${snippets.imports}
            ${snippets.body}
        """

        settingsFile("""
            pluginManagement {
                includeBuild('included')
            }
        """)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("Settings file '${relativePath(file)}': external process started")
        }

        where:
        snippetsFactory   | file
        exec().groovy     | "included/settings.gradle"
        javaexec().groovy | "included/settings.gradle"
        exec().kotlin     | "included/settings.gradle.kts"
        javaexec().kotlin | "included/settings.gradle.kts"
    }

    def "using #snippetsFactory.summary in included plugin build #file is a problem"(SnippetsFactory snippetsFactory,
                                                                                     String file) {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        def includedBuildFile = testDirectory.file(file)
        includedBuildFile << """
            ${snippets.imports}
            plugins {
                id("groovy-gradle-plugin")
            }
            ${snippets.body}
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
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file '${relativePath(file)}': external process started")
        }

        where:
        snippetsFactory   | file
        exec().groovy     | "included/build.gradle"
        javaexec().groovy | "included/build.gradle"
        exec().kotlin     | "included/build.gradle.kts"
        javaexec().kotlin | "included/build.gradle.kts"
    }

    def "using #snippetsFactory.summary in convention plugin #file is a problem"(SnippetsFactory snippetsFactory,
                                                                                 String file,
                                                                                 String plugin) {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file("buildSrc/build.gradle.kts") << """
            plugins {
                `$plugin`
            }

            repositories {
               mavenCentral()
            }
        """
        def conventionPluginFile = testDirectory.file(file)
        conventionPluginFile << """
            ${snippets.imports}

            ${snippets.body}
        """

        buildFile("""
            plugins {
                id("test-convention-plugin")
            }
        """)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("Plugin 'test-convention-plugin': external process started")
        }

        where:
        snippetsFactory   | file                                                         | plugin
        exec().groovy     | "buildSrc/src/main/groovy/test-convention-plugin.gradle"     | "groovy-gradle-plugin"
        javaexec().groovy | "buildSrc/src/main/groovy/test-convention-plugin.gradle"     | "groovy-gradle-plugin"
        exec().kotlin     | "buildSrc/src/main/kotlin/test-convention-plugin.gradle.kts" | "kotlin-dsl"
        javaexec().kotlin | "buildSrc/src/main/kotlin/test-convention-plugin.gradle.kts" | "kotlin-dsl"
    }

    def "using #snippetsFactory.summary in java project plugin application is a problem"(SnippetsFactory snippetsFactory) {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file("buildSrc/src/main/java/SneakyPlugin.java") << """
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${Plugin.name};
            import ${Project.name};
            ${snippets.imports}

            public abstract class SneakyPlugin implements Plugin<Project> {
                @Inject
                protected abstract ExecOperations getExecOperations();

                @Override
                public void apply(Project project) {
                    ${snippets.body}
                }
            }
        """

        buildFile("""
            apply plugin: SneakyPlugin
        """)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("Plugin class 'SneakyPlugin': external process started")
        }

        where:
        snippetsFactory                      | _
        exec("project").java                 | _
        javaexec("project").java             | _
        exec("getExecOperations()").java     | _
        javaexec("getExecOperations()").java | _
    }

    def "using #snippetsFactory.summary in java settings plugin application is a problem"(SnippetsFactory snippetsFactory) {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file("included/settings.gradle") << """
            rootProject.name="included"
        """

        testDirectory.file("included/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }

            gradlePlugin {
                plugins {
                    sneakyPlugin {
                        id = 'org.example.sneaky'
                        implementationClass = 'SneakyPlugin'
                    }
                }
            }
        """

        testDirectory.file("included/src/main/java/SneakyPlugin.java") << """
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${Plugin.name};
            import ${Settings.name};
            ${snippets.imports}

            public abstract class SneakyPlugin implements Plugin<Settings> {
                @Inject
                protected abstract ExecOperations getExecOperations();

                @Override
                public void apply(Settings project) {
                    ${snippets.body}
                }
            }
        """

        settingsFile("""
            pluginManagement {
                includeBuild("included")
            }

            plugins {
                id ("org.example.sneaky")
            }
        """)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("Plugin 'org.example.sneaky': external process started")
        }

        where:
        snippetsFactory                      | _
        exec("getExecOperations()").java     | _
        javaexec("getExecOperations()").java | _
    }

    def "using #snippetsFactory.summary in task configuration is a problem"(SnippetsFactory snippetsFactory) {
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
            // TODO(mlopatkin): Fix location there
            withProblem("Unknown location: external process started")
        }

        where:
        snippetsFactory                 | _
        exec("getProject()").java       | _
        javaexec("getProject()").java   | _
        exec("getExecOperations()").java     | _
        javaexec("getExecOperations()").java | _
    }

    def "using #snippetsFactory.summary in task action is not a problem"(SnippetsFactory snippetsFactory) {
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
    }

    def "using #snippetsFactory.summary in task action of buildSrc is not a problem"(SnippetsFactory snippetsFactory) {
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
    }

    def "using #snippetsFactory.summary in worker task action of buildSrc is not a problem"(SnippetsFactory snippetsFactory) {
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
    }

    def "using #snippetsFactory.summary in task of included plugin build is not a problem"(SnippetsFactory snippetsFactory) {
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
    }

    def "using #snippetsFactory.summary in task up-to-date is not a problem"(SnippetsFactory snippetsFactory) {
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
    }

    def "using #snippetsFactory.summary in up-to-date task of buildSrc is not a problem"(SnippetsFactory snippetsFactory) {
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
    }
}
