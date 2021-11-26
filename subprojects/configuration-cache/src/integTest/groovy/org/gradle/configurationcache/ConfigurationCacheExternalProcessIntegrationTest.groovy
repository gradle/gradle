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
import org.gradle.configurationcache.fixtures.ExecOperationsFixture.ExecFormatter
import org.gradle.process.ExecOperations

import javax.inject.Inject
import java.util.function.Function

class ConfigurationCacheExternalProcessIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    ExecOperationsFixture execOperationsFixture = new ExecOperationsFixture(testDirectory)

    def "using #method in #location.toLowerCase() #file is a problem"(String method,
                                                                      String file,
                                                                      String location,
                                                                      Function<ExecOperationsFixture, ExecFormatter> formatterFactory,
                                                                      Function<ExecFormatter, String> spec) {
        given:
        def formatter = formatterFactory.apply(execOperationsFixture)
        testDirectory.file(file) << """
            ${formatter.imports()}
            ${formatter.callProcessAndPrintOutput(method, spec.apply(formatter))}
        """

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("$location '$file': external process started")
        }

        where:
        method     | file                           | location        | formatterFactory                       | spec
        "exec"     | "build.gradle"                 | "Build file"    | ExecOperationsFixture::groovyFormatter | ExecFormatter::execSpec
        "javaexec" | "build.gradle"                 | "Build file"    | ExecOperationsFixture::groovyFormatter | ExecFormatter::javaexecSpec
        "exec"     | "build.gradle.kts"             | "Build file"    | ExecOperationsFixture::kotlinFormatter | ExecFormatter::execSpec
        "javaexec" | "build.gradle.kts"             | "Build file"    | ExecOperationsFixture::kotlinFormatter | ExecFormatter::javaexecSpec
        "exec"     | "settings.gradle"              | "Settings file" | ExecOperationsFixture::groovyFormatter | ExecFormatter::execSpec
        "javaexec" | "settings.gradle"              | "Settings file" | ExecOperationsFixture::groovyFormatter | ExecFormatter::javaexecSpec
        "exec"     | "settings.gradle.kts"          | "Settings file" | ExecOperationsFixture::kotlinFormatter | ExecFormatter::execSpec
        "javaexec" | "settings.gradle.kts"          | "Settings file" | ExecOperationsFixture::kotlinFormatter | ExecFormatter::javaexecSpec
        "exec"     | "buildSrc/build.gradle"        | "Build file"    | ExecOperationsFixture::groovyFormatter | ExecFormatter::execSpec
        "javaexec" | "buildSrc/build.gradle"        | "Build file"    | ExecOperationsFixture::groovyFormatter | ExecFormatter::javaexecSpec
        "exec"     | "buildSrc/build.gradle.kts"    | "Build file"    | ExecOperationsFixture::kotlinFormatter | ExecFormatter::execSpec
        "javaexec" | "buildSrc/build.gradle.kts"    | "Build file"    | ExecOperationsFixture::kotlinFormatter | ExecFormatter::javaexecSpec
        "exec"     | "buildSrc/settings.gradle"     | "Settings file" | ExecOperationsFixture::groovyFormatter | ExecFormatter::execSpec
        "javaexec" | "buildSrc/settings.gradle"     | "Settings file" | ExecOperationsFixture::groovyFormatter | ExecFormatter::javaexecSpec
        "exec"     | "buildSrc/settings.gradle.kts" | "Settings file" | ExecOperationsFixture::kotlinFormatter | ExecFormatter::execSpec
        "javaexec" | "buildSrc/settings.gradle.kts" | "Settings file" | ExecOperationsFixture::kotlinFormatter | ExecFormatter::javaexecSpec
    }

    def "using #method in initialization script #file is a problem"(String method, String file, Function<ExecOperationsFixture, ExecFormatter> formatterFactory, Function<ExecFormatter, String> spec) {
        given:
        def formatter = formatterFactory.apply(execOperationsFixture)

        def initScriptFile = testDirectory.file(file)
        initScriptFile << """
            ${formatter.imports()}
            ${formatter.callProcessAndPrintOutput(method, spec.apply(formatter))}
        """
        executer.usingInitScript(initScriptFile)

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("Initialization script '$file': external process started")
        }

        where:
        method     | file                   | formatterFactory                       | spec
        "exec"     | "exec.init.gradle"     | ExecOperationsFixture::groovyFormatter | ExecFormatter::execSpec
        "javaexec" | "exec.init.gradle"     | ExecOperationsFixture::groovyFormatter | ExecFormatter::javaexecSpec
        "exec"     | "exec.init.gradle.kts" | ExecOperationsFixture::kotlinFormatter | ExecFormatter::execSpec
        "javaexec" | "exec.init.gradle.kts" | ExecOperationsFixture::kotlinFormatter | ExecFormatter::javaexecSpec
    }

    def "using #method in included plugin settings #file is a problem"(String method,
                                                                       String file,
                                                                       Function<ExecOperationsFixture, ExecFormatter> formatterFactory,
                                                                       Function<ExecFormatter, String> spec) {
        given:
        def formatter = formatterFactory.apply(execOperationsFixture)
        testDirectory.file(file) << """
            ${formatter.imports()}
            ${formatter.callProcessAndPrintOutput(method, spec.apply(formatter))}
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
            withProblem("Settings file '$file': external process started")
        }

        where:
        method     | file                           | formatterFactory                       | spec
        "exec"     | "included/settings.gradle"     | ExecOperationsFixture::groovyFormatter | ExecFormatter::execSpec
        "javaexec" | "included/settings.gradle"     | ExecOperationsFixture::groovyFormatter | ExecFormatter::javaexecSpec
        "exec"     | "included/settings.gradle.kts" | ExecOperationsFixture::kotlinFormatter | ExecFormatter::execSpec
        "javaexec" | "included/settings.gradle.kts" | ExecOperationsFixture::kotlinFormatter | ExecFormatter::javaexecSpec
    }

    def "using #method in included plugin build #file is a problem"(String method,
                                                                    String file,
                                                                    Function<ExecOperationsFixture, ExecFormatter> formatterFactory,
                                                                    Function<ExecFormatter, String> spec) {
        given:
        def formatter = formatterFactory.apply(execOperationsFixture)
        def includedBuildFile = testDirectory.file(file)
        includedBuildFile << """
            ${formatter.imports()}
            plugins {
                id("groovy-gradle-plugin")
            }
            ${formatter.callProcessAndPrintOutput(method, spec.apply(formatter))}
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
            withProblem("Build file '$file': external process started")
        }

        where:
        method     | file                        | formatterFactory                       | spec
        "exec"     | "included/build.gradle"     | ExecOperationsFixture::groovyFormatter | ExecFormatter::execSpec
        "javaexec" | "included/build.gradle"     | ExecOperationsFixture::groovyFormatter | ExecFormatter::javaexecSpec
        "exec"     | "included/build.gradle.kts" | ExecOperationsFixture::kotlinFormatter | ExecFormatter::execSpec
        "javaexec" | "included/build.gradle.kts" | ExecOperationsFixture::kotlinFormatter | ExecFormatter::javaexecSpec
    }

    def "using #method in convention plugin #file is a problem"(String method,
                                                                String file,
                                                                String plugin,
                                                                Function<ExecOperationsFixture, ExecFormatter> formatterFactory,
                                                                Function<ExecFormatter, String> spec) {
        given:
        def formatter = formatterFactory.apply(execOperationsFixture)
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
            ${formatter.imports()}

            ${formatter.callProcessAndPrintOutput(method, spec.apply(formatter))}
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
        method     | file                                                         | plugin                 | formatterFactory                       | spec
        "exec"     | "buildSrc/src/main/groovy/test-convention-plugin.gradle"     | "groovy-gradle-plugin" | ExecOperationsFixture::groovyFormatter | ExecFormatter::execSpec
        "javaexec" | "buildSrc/src/main/groovy/test-convention-plugin.gradle"     | "groovy-gradle-plugin" | ExecOperationsFixture::groovyFormatter | ExecFormatter::javaexecSpec
        "exec"     | "buildSrc/src/main/kotlin/test-convention-plugin.gradle.kts" | "kotlin-dsl"           | ExecOperationsFixture::kotlinFormatter | ExecFormatter::execSpec
        "javaexec" | "buildSrc/src/main/kotlin/test-convention-plugin.gradle.kts" | "kotlin-dsl"           | ExecOperationsFixture::kotlinFormatter | ExecFormatter::javaexecSpec
    }

    def "using #method in java project plugin application is a problem"(String method,
                                                                        Function<ExecFormatter, String> spec) {
        given:
        def formatter = execOperationsFixture.javaFormatter()
        testDirectory.file("buildSrc/src/main/java/SneakyPlugin.java") << """
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${Plugin.name};
            import ${Project.name};
            ${formatter.imports()}

            public abstract class SneakyPlugin implements Plugin<Project> {
                @Inject
                protected abstract ExecOperations getExecOperations();

                @Override
                public void apply(Project project) {
                    ${formatter.callProcessAndPrintOutput(method, spec.apply(formatter))}
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
        method                         | spec
        "project.exec"                 | ExecFormatter::execSpec
        "project.javaexec"             | ExecFormatter::javaexecSpec
        "getExecOperations().exec"     | ExecFormatter::execSpec
        "getExecOperations().javaexec" | ExecFormatter::javaexecSpec
    }

    def "using #method in java settings plugin application is a problem"(String method,
                                                                         Function<ExecFormatter, String> spec) {
        given:
        def formatter = execOperationsFixture.javaFormatter()
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
            ${formatter.imports()}

            public abstract class SneakyPlugin implements Plugin<Settings> {
                @Inject
                protected abstract ExecOperations getExecOperations();

                @Override
                public void apply(Settings project) {
                    ${formatter.callProcessAndPrintOutput(method, spec.apply(formatter))}
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
        method                         | spec
        "getExecOperations().exec"     | ExecFormatter::execSpec
        "getExecOperations().javaexec" | ExecFormatter::javaexecSpec
    }

    def "using #method in task configuration is a problem"(String method,
                                                           Function<ExecFormatter, String> spec) {
        given:
        def formatter = execOperationsFixture.javaFormatter()
        testDirectory.file("buildSrc/src/main/java/SneakyTask.java") << """
            import ${DefaultTask.name};
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${TaskAction.name};
            ${formatter.imports()}

            public abstract class SneakyTask extends DefaultTask {
                @Inject
                protected abstract ExecOperations getExecOperations();

                public SneakyTask() {
                    ${formatter.callProcessAndPrintOutput(method, spec.apply(formatter))}
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
        method                         | spec
        "getProject().exec"            | ExecFormatter::execSpec
        "getProject().javaexec"        | ExecFormatter::javaexecSpec
        "getExecOperations().exec"     | ExecFormatter::execSpec
        "getExecOperations().javaexec" | ExecFormatter::javaexecSpec
    }

    def "using #method in task action is not a problem"(String method,
                                                        Function<ExecFormatter, String> spec) {
        given:
        def formatter = execOperationsFixture.javaFormatter()
        testDirectory.file("buildSrc/src/main/java/SneakyTask.java") << """
            import ${DefaultTask.name};
            import ${ExecOperations.name};
            import ${Inject.name};
            import ${TaskAction.name};
            ${formatter.imports()}

            public abstract class SneakyTask extends DefaultTask {
                @Inject
                protected abstract ExecOperations getExecOperations();

                @TaskAction
                public void exec() {
                    ${formatter.callProcessAndPrintOutput(method, spec.apply(formatter))}
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
        method                         | spec
        "getExecOperations().exec"     | ExecFormatter::execSpec
        "getExecOperations().javaexec" | ExecFormatter::javaexecSpec
    }
}
