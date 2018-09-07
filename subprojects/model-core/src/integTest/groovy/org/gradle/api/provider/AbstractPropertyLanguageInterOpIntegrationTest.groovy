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

package org.gradle.api.provider

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.jcenterRepository

@Requires(TestPrecondition.KOTLIN_SCRIPT)
abstract class AbstractPropertyLanguageInterOpIntegrationTest extends AbstractIntegrationSpec {
    private hasKotlin = false

    TestFile pluginDir = file("buildSrc/plugin")

    void usesKotlin(TestFile dir) {
        def buildfile = dir.file("build.gradle.kts")
        if (!buildfile.file) {
            buildfile.createFile()
        }
        def block = """
            plugins { `kotlin-dsl` }
            ${jcenterRepository(GradleDsl.KOTLIN)}
        """
        buildfile.text = block + buildfile.text
        if (!hasKotlin) {
            executer.beforeExecute {
                expectDeprecationWarning()
                // Run Kotlin compiler in-process to avoid file locking issues
                executer.withArguments("-Dkotlin.compiler.execution.strategy=in-process")
            }
            hasKotlin = true
        }
    }

    def setup() {
        executer.withPluginRepositoryMirror()
        file("buildSrc/settings.gradle") << """
            include("plugin")
        """
        file("buildSrc/build.gradle.kts") << """
            dependencies {
                compile(project(":plugin"))
            }
        """
    }

    abstract void pluginSetsValues()

    abstract void pluginSetsCalculatedValues()

    abstract void pluginDefinesTask()

    def "can define property and set value from language plugin"() {
        pluginSetsValues()

        buildFile << """
            apply plugin: SomePlugin
        """
        when:
        run("someTask")

        then:
        outputContains("flag = true")
        outputContains("message = some value")
    }

    def "can define property and set calculated value from language plugin"() {
        pluginSetsCalculatedValues()

        buildFile << """
            apply plugin: SomePlugin
        """
        when:
        run("someTask")

        then:
        outputContains("flag = true")
        outputContains("message = some value")
    }

    def "can define property in language plugin and set value from Groovy DSL"() {
        pluginDefinesTask()

        buildFile << """
            apply plugin: SomePlugin
            tasks.someTask {
                flag = true
                message = "some value"
            }
        """
        when:
        run("someTask")

        then:
        outputContains("flag = true")
        outputContains("message = some value")

        when:
        buildFile << """
            tasks.someTask {
                flag = provider { false }
                message = provider { "some new value" }
            }
        """
        run("someTask")

        then:
        outputContains("flag = false")
        outputContains("message = some new value")
    }

    def "can define property in language plugin and set value from Kotlin DSL"() {
        pluginDefinesTask()

        file("build.gradle.kts") << """
            plugins.apply(SomePlugin::class.java)
            tasks.withType(SomeTask::class.java).named("someTask").configure {
                flag.set(true)
                message.set("some value")
            }
        """

        when:
        run("someTask")

        then:
        outputContains("flag = true")
        outputContains("message = some value")

        when:
        file("build.gradle.kts") << """
            tasks.withType(SomeTask::class.java).named("someTask").configure {
                flag.set(provider { false })
                message.set(provider { "some new value" })
            }
        """
        run("someTask")

        then:
        outputContains("flag = false")
        outputContains("message = some new value")
    }

    def "can define property in language plugin and set value from Java plugin"() {
        pluginDefinesTask()

        file("buildSrc/settings.gradle") << """
            include("other")
        """
        file("buildSrc/build.gradle.kts") << """
            dependencies {
                compile(project(":other"))
            }
        """
        def otherDir = file("buildSrc/other")
        otherDir.file("build.gradle") << """
            plugins { 
                id("java-library")
            }
            dependencies {
                api gradleApi()
                implementation project(":plugin")
            }
        """

        otherDir.file("src/main/java/SomeOtherPlugin.java") << """
            import ${Project.name};
            import ${Plugin.name};

            public class SomeOtherPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().withType(SomeTask.class).configureEach(t -> {
                        t.getFlag().set(false);
                        t.getMessage().set("some other value");
                    });
                }
            }
        """

        buildFile << """
            apply plugin: SomeOtherPlugin
            apply plugin: SomePlugin
        """

        when:
        run("someTask")

        then:
        outputContains("flag = false")
        outputContains("message = some other value")
    }

    def "can define property in language plugin and set value from Kotlin plugin"() {
        pluginDefinesTask()

        file("buildSrc/settings.gradle") << """
            include("other")
        """
        file("buildSrc/build.gradle.kts") << """
            dependencies {
                compile(project(":other"))
            }
        """

        def otherDir = file("buildSrc/other")
        usesKotlin(file(otherDir))
        // This is because the Kotlin compiler is run in-process (to avoid issues with the Kotlin compiler daemon) and also keeps jars open
        executer.requireDaemon().requireIsolatedDaemons()
        otherDir.file("build.gradle.kts") << """
            dependencies {
                implementation(project(":plugin"))
            }
        """

        otherDir.file("src/main/kotlin/SomeOtherPlugin.kt") << """
            import ${Project.name}
            import ${Plugin.name}

            class SomeOtherPlugin: Plugin<Project> {
                override fun apply(project: Project) {
                    project.tasks.withType(SomeTask::class.java).configureEach {
                        flag.set(false)
                        message.set("some other value")
                    }
                }
            }
        """

        buildFile << """
            apply plugin: SomeOtherPlugin
            apply plugin: SomePlugin
        """

        when:
        // Due to exception logged by Kotlin plugin
        executer.withStackTraceChecksDisabled()
        run("someTask")

        then:
        outputContains("flag = false")
        outputContains("message = some other value")
    }
}
