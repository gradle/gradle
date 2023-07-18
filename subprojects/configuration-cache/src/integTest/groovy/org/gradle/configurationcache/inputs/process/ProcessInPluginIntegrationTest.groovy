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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.process.ExecOperations
import org.gradle.test.fixtures.dsl.GradleDsl

import javax.inject.Inject

import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.exec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.javaexec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.processBuilder
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.runtimeExec
import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.stringArrayExecute

class ProcessInPluginIntegrationTest extends AbstractProcessIntegrationTest {
    def "using #snippetsFactory.summary in convention plugin #file is a problem"() {
        given:
        def snippets = snippetsFactory.newSnippets(execOperationsFixture)
        testDirectory.file("buildSrc/build.gradle.kts") << """
            plugins {
                `$plugin`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}
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

        executer.noDeprecationChecks()

        when:
        configurationCacheFails(":help")

        then:
        failure.assertOutputContains("Hello")
        problems.assertFailureHasProblems(failure) {
            withProblem("Plugin 'test-convention-plugin': external process started")
        }

        where:
        snippetsFactory             | file
        exec().groovy               | "buildSrc/src/main/groovy/test-convention-plugin.gradle"
        javaexec().groovy           | "buildSrc/src/main/groovy/test-convention-plugin.gradle"
        processBuilder().groovy     | "buildSrc/src/main/groovy/test-convention-plugin.gradle"
        stringArrayExecute().groovy | "buildSrc/src/main/groovy/test-convention-plugin.gradle"
        runtimeExec().groovy        | "buildSrc/src/main/groovy/test-convention-plugin.gradle"
        exec().kotlin               | "buildSrc/src/main/kotlin/test-convention-plugin.gradle.kts"
        javaexec().kotlin           | "buildSrc/src/main/kotlin/test-convention-plugin.gradle.kts"
        processBuilder().kotlin     | "buildSrc/src/main/kotlin/test-convention-plugin.gradle.kts"
        stringArrayExecute().kotlin | "buildSrc/src/main/kotlin/test-convention-plugin.gradle.kts"
        runtimeExec().kotlin        | "buildSrc/src/main/kotlin/test-convention-plugin.gradle.kts"

        plugin = file.endsWith(".kts") ? "kotlin-dsl" : "groovy-gradle-plugin"
    }

    def "using #snippetsFactory.summary in java project plugin application is a problem"() {
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
        processBuilder().java                | _
        stringArrayExecute().java            | _
        runtimeExec().java                   | _
    }

    def "using #snippetsFactory.summary in java settings plugin application is a problem"() {
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
        processBuilder().java                | _
        stringArrayExecute().java            | _
        runtimeExec().java                   | _
    }
}
