/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.plugin.devel.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.file.TestFile

class PreCompiledScriptPluginsPluginIntegrationTest extends AbstractIntegrationSpec {

    private static final String SAMPLE_TASK = "sampleTask"
    private static final String REGISTER_SAMPLE_TASK = """
            tasks.register("$SAMPLE_TASK") {}
        """

    @ToBeFixedForInstantExecution
    def "adds plugin metadata to extension for all script plugins"() {
        createDir("buildSrc/src/main/groovy/plugins").file("foo.gradle").createNewFile()
        createDir("buildSrc/src/main/groovy/plugins").file("bar.gradle").createNewFile()
        enablePrecompiledPluginsInBuildSrc()

        file("buildSrc/build.gradle") << """
            afterEvaluate {
                gradlePlugin.plugins.all {
                    println it.id + ": " + it.implementationClass
                }
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("foo: FooPlugin")
        outputContains("bar: BarPlugin")
    }

    @ToBeFixedForInstantExecution
    def "can apply a precompiled script plugin by id"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")
        enablePrecompiledPluginsInBuildSrc()

        pluginDir.file("foo.gradle") << """
            plugins {
                id 'base'
            }
            logger.lifecycle "foo script plugin applied"
        """


        buildFile << """
            plugins {
                id 'foo'
            }
        """

        expect:
        succeeds("clean")

        and:
        outputContains("foo script plugin applied")
    }

    @ToBeFixedForInstantExecution
    def "can share precompiled plugin via a jar"() {
        given:
        def pluginJar = packagePrecompiledPlugin("foo.gradle")

        when:
        settingsFile << """
            buildscript {
                dependencies {
                    classpath(files("$pluginJar"))
                }
            }
        """

        buildFile << """
            plugins {
                id 'foo'
            }
        """

        then:
        succeeds(SAMPLE_TASK)
    }

    @ToBeFixedForInstantExecution
    def "can share multiple precompiled plugins via a jar"() {
        given:
        def pluginsJar = packagePrecompiledPlugins([
            "foo.gradle": 'tasks.register("firstPluginTask") {}',
            "bar.gradle": 'tasks.register("secondPluginTask") {}',
            "fizz.buzz.foo-bar.gradle": 'tasks.register("thirdPluginTask") {}'
        ])

        when:
        settingsFile << """
            buildscript {
                dependencies {
                    classpath(files("$pluginsJar"))
                }
            }
        """

        buildFile << """
            plugins {
                id 'foo'
                id 'bar'
                id 'fizz.buzz.foo-bar'
            }
        """

        then:
        succeeds("firstPluginTask")
        succeeds("secondPluginTask")
        succeeds("thirdPluginTask")
    }

    @ToBeFixedForInstantExecution
    def "can share precompiled plugin by publishing it"() {
        given:
        def pluginDir = createDir("plugin/src/main/groovy/plugins")
        pluginWithSampleTask(pluginDir, "foo.bar.my-plugin.gradle")
        file("plugin/build.gradle") << """
            plugins {
                id 'precompiled-groovy-plugin'
                id 'maven-publish'
            }
            group = 'com.example'
            version = '1.0'
            publishing {
                repositories {
                    maven {
                        url '${mavenRepo.uri}'
                    }
                }
            }
        """

        when:
        executer.inDirectory(file("plugin")).withTasks("publish").run()
        mavenRepo.module('com.example', 'plugin', '1.0').assertPublished()
        file('plugin').forceDeleteDir()

        settingsFile << """
            pluginManagement {
                repositories {
                    maven {
                        url '${mavenRepo.uri}'
                    }
                }
            }
        """

        buildFile << """
            plugins {
                id 'foo.bar.my-plugin' version '1.0'
            }
        """

        then:
        succeeds(SAMPLE_TASK)
    }

    @ToBeFixedForInstantExecution
    def "can apply a precompiled settings plugin by id"() {
        given:
        def pluginJar = packagePrecompiledPlugin("my-settings-plugin.settings.gradle", """
            println("my-settings-plugin applied!")
        """)

        when:
        settingsFile << """
            buildscript {
                dependencies {
                    classpath(files("$pluginJar"))
                }
            }
            apply plugin: 'my-settings-plugin'
        """

        then:
        succeeds("help")
        outputContains("my-settings-plugin applied!")
    }

    @ToBeFixedForInstantExecution
    def "can apply a precompiled init plugin"() {
        given:
        def pluginJar = packagePrecompiledPlugin("my-init-plugin.init.gradle", """
            println("my-init-plugin applied!")
        """)

        when:
        settingsFile << """
            buildscript {
                dependencies {
                    classpath(files("$pluginJar"))
                }
            }
            apply(plugin: MyInitPluginPlugin, to: gradle)
        """

        then:
        succeeds("help")
        outputContains("my-init-plugin applied!")
    }

    @ToBeFixedForInstantExecution
    def "can use kebab-case in plugin id"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/")
        enablePrecompiledPluginsInBuildSrc()

        pluginWithSampleTask(pluginDir, "my-plugin.gradle")

        buildFile << """
            plugins {
                id 'my-plugin'
            }
        """

        expect:
        succeeds(SAMPLE_TASK)
    }

    @ToBeFixedForInstantExecution
    def "plugin without package can declare fully qualified id in file name"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/")
        enablePrecompiledPluginsInBuildSrc()

        pluginWithSampleTask(pluginDir, "foo.bar.my-plugin.gradle")

        buildFile << """
            plugins {
                id 'foo.bar.my-plugin'
            }
        """

        expect:
        succeeds(SAMPLE_TASK)
    }

    def "displays reasonable error message when plugin filename does not comply with plugin id naming rules"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/")
        enablePrecompiledPluginsInBuildSrc()

        pluginWithSampleTask(pluginDir, "foo.bar.m%y-plugin.gradle")

        expect:
        fails("help")
        failureCauseContains("plugin id 'foo.bar.m%y-plugin' is invalid")
    }

    @ToBeFixedForInstantExecution
    def "can apply a precompiled script plugin by id that applies another precompiled script plugin by id"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")
        enablePrecompiledPluginsInBuildSrc()

        pluginDir.file("foo.gradle") << """
            plugins {
                id 'base'
            }

            logger.lifecycle "foo script plugin applied"
        """

        pluginDir.file("bar.gradle") << """
            plugins {
                id 'foo'
            }

            logger.lifecycle "bar script plugin applied"
        """

        buildFile << """
            plugins {
                id 'bar'
            }
        """

        expect:
        succeeds("clean")

        and:
        outputContains("bar script plugin applied")
        outputContains("foo script plugin applied")
    }

    @ToBeFixedForInstantExecution
    def "can apply configuration in a precompiled script plugin to the current project"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")
        enablePrecompiledPluginsInBuildSrc()

        pluginDir.file("foo.gradle") << """
            sourceSets.main.java.srcDir 'src'
        """

        createDir("src").file("Foo.java") << """
            public class Foo { }
        """

        buildFile << """
            plugins {
                id 'java'
                id 'foo'
            }
        """

        expect:
        succeeds("classes")

        and:
        executedAndNotSkipped(":compileJava")

        and:
        file("build/classes/java/main/Foo.class").exists()
    }

    @ToBeFixedForInstantExecution
    def "can apply and configure a plugin in a precompiled script plugin"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")
        enablePrecompiledPluginsInBuildSrc()

        pluginDir.file("foo.gradle") << """
            plugins {
                id 'java'
            }

            sourceSets.main.java.srcDir 'src'
        """

        testDirectory.createDir("src").file("Foo.java") << """
            public class Foo { }
        """

        buildFile << """
            plugins {
                id 'foo'
            }
        """

        expect:
        succeeds("classes")

        and:
        executedAndNotSkipped(":compileJava")

        and:
        file("build/classes/java/main/Foo.class").exists()
    }

    @ToBeFixedForInstantExecution
    def "can add tasks in a precompiled script plugin"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")
        enablePrecompiledPluginsInBuildSrc()

        pluginDir.file("foo.gradle") << """
            task doSomething {
                doFirst { println "from foo plugin" }
            }
        """

        buildFile << """
            plugins {
                id 'foo'
            }

            doSomething {
                doLast {
                    println "from main build script"
                }
            }
        """

        expect:
        succeeds("doSomething")

        and:
        outputContains("from foo plugin")
        outputContains("from main build script")
    }

    @ToBeFixedForInstantExecution
    def "can use Gradle API classes directly in precompiled script plugin"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")
        enablePrecompiledPluginsInBuildSrc()

        pluginScript(pluginDir, "test-plugin.gradle", """
            class TestTask extends DefaultTask {
                @TaskAction
                void run() {
                    println 'from custom task'
                }
            }

            task testTask(type: TestTask)
        """)

        buildFile << """
            plugins {
                id 'test-plugin'
            }
        """

        expect:
        succeeds("testTask")

        and:
        outputContains("from custom task")
    }

    @ToBeFixedForInstantExecution
    def "can write tests for precompiled script plugins"() {
        when:
        def pluginDir = createDir("src/main/groovy/")
        pluginWithSampleTask(pluginDir, "test-plugin.gradle")

        file("src/test/groovy/Test.groovy") << """
            import org.gradle.testfixtures.ProjectBuilder
            import org.gradle.api.Project
            import spock.lang.Specification

            class Test extends Specification {
                def "plugin registers sample task"() {
                    given:
                    def project = ProjectBuilder.builder().build()

                    when:
                    project.plugins.apply("test-plugin")

                    then:
                    project.tasks.findByName("$SAMPLE_TASK") != null
                }
            }
        """

        settingsFile << """
            rootProject.name = 'test'
        """

        buildFile << """
            plugins {
                id 'precompiled-groovy-plugin'
            }
            repositories { mavenCentral() }
            dependencies {
                testImplementation 'org.spockframework:spock-core:1.3-groovy-2.5'
            }
        """

        then:
        succeeds 'build'
        executedAndNotSkipped ':test'
    }

    @ToBeFixedForInstantExecution
    def "precompiled script plugins tasks are cached and relocatable"() {
        given:
        def cacheDir = createDir("cache-dir")
        def firstDir = createDir("first-location")

        file("$firstDir.name/settings.gradle") << """
            rootProject.name = "test"
            buildCache {
                local {
                    directory = file("../${cacheDir.name}")
                }
            }
        """
        file("$firstDir.name/build.gradle") << """
            plugins {
                id 'precompiled-groovy-plugin'
            }
        """
        pluginWithSampleTask(firstDir.file("src/main/groovy/"), "my-plugin.gradle")

        def secondDir = createDir("second-location")
        firstDir.copyTo(secondDir)

        def cachedTasks = [
            ":preCompileScriptPlugins",
            ":compileJava"
        ]

        def result = executer.inDirectory(firstDir).withTasks("classes").withArgument("--build-cache").run()
        cachedTasks.forEach {
            result.assertTaskExecuted(it)
        }

        result = executer.inDirectory(firstDir).withTasks("classes").withArgument("--build-cache").run()
        cachedTasks.forEach {
            result.assertOutputContains("$it UP-TO-DATE")
        }

        result = executer.inDirectory(secondDir).withTasks("classes").withArgument("--build-cache").run()
        cachedTasks.forEach {
            result.assertOutputContains("$it FROM-CACHE")
        }
    }

    @ToBeFixedForInstantExecution
    def "can apply precompiled Groovy script plugin from Kotlin script"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")
        enablePrecompiledPluginsInBuildSrc()

        pluginWithSampleTask(pluginDir, "foo.gradle")

        buildKotlinFile << """
            plugins {
                foo
            }
        """

        expect:
        succeeds(SAMPLE_TASK)
    }

    private String packagePrecompiledPlugin(String pluginFile, String pluginContent = REGISTER_SAMPLE_TASK) {
        Map<String, String> plugins = [:]
        plugins.putAt(pluginFile, pluginContent)
        return packagePrecompiledPlugins(plugins)
    }

    private String packagePrecompiledPlugins(Map<String, String> pluginToContent) {
        def pluginDir = createDir("plugins/src/main/groovy/plugins")
        pluginToContent.each { pluginFile, pluginContent ->
            pluginScript(pluginDir, pluginFile, pluginContent)
        }
        file("plugins/build.gradle") << """
            plugins {
                id 'precompiled-groovy-plugin'
            }
        """

        executer.inDirectory(file("plugins")).withTasks("jar").run()
            .assertNotOutput("No valid plugin descriptors were found in META-INF/gradle-plugins")
        def pluginJar = file("plugins/build/libs/plugins.jar").assertExists()
        def movedJar = file('plugins.jar')
        pluginJar.renameTo(movedJar)
        file('plugins').forceDeleteDir()
        return movedJar.name
    }

    private void enablePrecompiledPluginsInBuildSrc() {
        file("buildSrc/build.gradle") << """
            plugins {
                id 'precompiled-groovy-plugin'
            }
        """
    }

    private static void pluginWithSampleTask(TestFile pluginDir, String pluginFile) {
        pluginScript(pluginDir, pluginFile, REGISTER_SAMPLE_TASK)
    }

    private static void pluginScript(TestFile pluginDir, String pluginFile, String content) {
        pluginDir.file(pluginFile) << content
    }
}
