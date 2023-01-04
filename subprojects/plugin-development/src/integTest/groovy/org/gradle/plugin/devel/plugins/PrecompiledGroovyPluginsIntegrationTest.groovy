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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf
import spock.lang.Issue

class PrecompiledGroovyPluginsIntegrationTest extends AbstractIntegrationSpec {

    private static final String SAMPLE_TASK = "sampleTask"
    private static final String REGISTER_SAMPLE_TASK = """
            tasks.register("$SAMPLE_TASK") {}
        """

    def "adds plugin metadata to extension for all script plugins"() {
        when:
        def pluginsDir = createDir("buildSrc/src/main/groovy/plugins")
        pluginsDir.file("foo.gradle").createNewFile()
        pluginsDir.file("bar.gradle").createNewFile()
        enablePrecompiledPluginsInBuildSrc()

        file("buildSrc/build.gradle") << """
            afterEvaluate {
                assert gradlePlugin.plugins.foo.implementationClass == 'FooPlugin'
                assert gradlePlugin.plugins.bar.implementationClass == 'BarPlugin'
            }
        """

        then:
        succeeds("help")
    }

    def "adding precompiled script support does not fail when there are no precompiled scripts"() {
        when:
        enablePrecompiledPluginsInBuildSrc()

        then:
        succeeds("help")
    }

    def "can apply a precompiled script plugin by id"() {
        given:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/foo.gradle") << """
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

        when:
        succeeds("clean")

        then:
        outputContains("foo script plugin applied")
    }

    def "can apply a precompiled script plugin by id to a multi-project build from root"() {
        given:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/foo.gradle") << """
            plugins {
                id 'base'
            }
            logger.lifecycle("hello from " + path)
        """

        buildFile << """
            plugins {
                id 'foo' apply false
            }
            allprojects {
                apply plugin: 'foo'
            }
        """
        settingsFile << """
            include 'a', 'b', 'c'
        """

        when:
        succeeds("help")

        then:
        outputContains("hello from :")
        outputContains("hello from :a")
        outputContains("hello from :b")
        outputContains("hello from :c")
    }

    def "can apply a precompiled script plugin by id to a multi-project build"() {
        given:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/foo.gradle") << """
            plugins {
                id 'base'
            }
            logger.lifecycle("hello from " + path)
        """

        [buildFile, file("a/build.gradle"), file("b/build.gradle"), file("c/build.gradle")].each { bf ->
            bf << """
                plugins {
                    id 'foo'
                }
            """
        }
        settingsFile << """
            include 'a', 'b', 'c'
        """

        when:
        succeeds("help")

        then:
        outputContains("hello from :")
        outputContains("hello from :a")
        outputContains("hello from :b")
        outputContains("hello from :c")
    }

    def "multiple plugins with same namespace do not clash"() {
        given:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/foo.bar.gradle") << "println 'foo.bar applied'"
        file("buildSrc/src/main/groovy/plugins/baz.bar.gradle") << "println 'baz.bar applied'"

        buildFile << """
            plugins {
                id 'foo.bar'
                id 'baz.bar'
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("foo.bar applied")
        outputContains("baz.bar applied")
    }

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

    def "removing plugins removes old adapters"() {
        given:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/foo.bar.gradle") << "println 'foo.bar applied'"
        file("buildSrc/src/main/groovy/plugins/baz.bar.gradle") << "println 'baz.bar applied'"

        buildFile << """
            plugins {
                id 'foo.bar'
            }
        """
        succeeds("help")

        when:
        file("buildSrc/src/main/groovy/plugins/baz.bar.gradle").delete()
        then:
        succeeds("help")

        when:
        file("buildSrc/src/main/groovy/plugins/foo.bar.gradle").delete()
        then:
        fails("help")
    }

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

    def "can share precompiled plugin by publishing it"() {
        given:
        pluginWithSampleTask("plugin/src/main/groovy/plugins/foo.bar.my-plugin.gradle")
        file("plugin/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
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

    def "can apply a precompiled settings plugin by id"() {
        given:
        file("plugin/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
        file("plugin/src/main/groovy/my-settings-plugin.settings.gradle") << """
            println("my-settings-plugin applied!")
        """

        when:
        settingsFile << """
            pluginManagement {
                includeBuild("plugin")
            }
            plugins {
                id("my-settings-plugin")
            }
        """

        then:
        succeeds("help")
        outputContains("my-settings-plugin applied!")
    }

    def "precompiled settings plugin can use plugins block"() {
        given:
        file("plugin/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
        file("plugin/src/main/groovy/base-settings-plugin.settings.gradle") << """
            println('base-settings-plugin applied!')
        """
        file("plugin/src/main/groovy/my-settings-plugin.settings.gradle") << """
            plugins {
                id 'base-settings-plugin'
            }
            println('my-settings-plugin applied!')
        """

        when:
        settingsFile << """
            pluginManagement {
                includeBuild("plugin")
            }
            plugins {
                id("my-settings-plugin")
            }
        """

        then:
        succeeds('help')
        outputContains('base-settings-plugin applied!')
        outputContains('my-settings-plugin applied!')
    }

    @Issue("https://github.com/gradle/gradle/issues/15416")
    def "precompiled settings plugin can use pluginManagement block"() {
        when:
        file("plugin/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
        file("plugin/src/main/groovy/my-settings-plugin.settings.gradle") << """
            pluginManagement {
                repositories {
                    println('pluginManagement block executed')
                    mavenCentral()
                }
            }
            println('my-settings-plugin applied!')
        """

        settingsFile << """
            pluginManagement {
                includeBuild("plugin")
            }
            plugins {
                id("my-settings-plugin")
            }
        """

        then:
        succeeds('help')
        outputContains('pluginManagement block executed')
        outputContains('my-settings-plugin applied!')
    }

    def "precompiled project plugin can not use pluginManagement block"() {
        when:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/foo.gradle") << """
            pluginManagement {
                repositories {
                    mavenCentral()
                }
            }
            println("foo project plugin applied")
        """

        then:
        fails("help")
        failureCauseContains("Only Settings scripts can contain a pluginManagement {} block.")
    }

    def "precompiled project plugin can not use buildscript block"() {
        when:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/foo.gradle") << """
            buildscript {
                dependencies {}
            }
            println("foo project plugin applied")
        """

        then:
        fails("help")
        failureCauseContains("The `buildscript` block is not supported in Groovy script plugins. Use the `plugins` block or project level dependencies instead.")
    }

    def "precompiled settings plugin can not use buildscript block"() {
        when:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/foo.settings.gradle") << """
            buildscript {
                dependencies {}
            }
            println("foo settings plugin applied")
        """

        then:
        fails('help')
        failureCauseContains("The `buildscript` block is not supported in Groovy script plugins. Use the `plugins` block or project level dependencies instead.")
    }

    def "can apply a precompiled init plugin"() {
        given:
        def pluginJar = packagePrecompiledPlugin("my-init-plugin.init.gradle", """
            println("my-init-plugin applied!")
        """)

        def initScript = file('init-script.gradle') << """
            initscript {
                dependencies {
                    classpath(files("$pluginJar"))
                }
            }

            apply plugin: MyInitPluginPlugin
        """

        when:
        executer.usingInitScript(initScript)

        then:
        succeeds("help")
        outputContains("my-init-plugin applied!")
    }

    def "precompiled init plugin can not use plugins block"() {
        given:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/my-init-plugin.init.gradle") << """
            plugins {
                id 'base'
            }
        """

        when:
        fails("build")

        then:
        failureCauseContains("Only Project and Settings build scripts can contain plugins {} blocks")
    }

    def "can use kebab-case in plugin id"() {
        when:
        enablePrecompiledPluginsInBuildSrc()
        pluginWithSampleTask("buildSrc/src/main/groovy/my-plugin.gradle")

        buildFile << """
            plugins {
                id 'my-plugin'
            }
        """

        then:
        succeeds(SAMPLE_TASK)
    }

    @Issue("https://github.com/gradle/gradle/issues/16459")
    def "can have .gradle substring within plugin id"() {
        when:
        enablePrecompiledPluginsInBuildSrc()
        pluginWithSampleTask("buildSrc/src/main/groovy/dev.gradlefoo.some-plugin.gradle")

        buildFile << """
            plugins {
                id 'dev.gradlefoo.some-plugin'
            }
        """

        then:
        succeeds(SAMPLE_TASK)
    }

    def "plugin without package can declare fully qualified id in file name"() {
        when:
        enablePrecompiledPluginsInBuildSrc()
        pluginWithSampleTask("buildSrc/src/main/groovy/foo.bar.my-plugin.gradle")

        buildFile << """
            plugins {
                id 'foo.bar.my-plugin'
            }
        """

        then:
        succeeds(SAMPLE_TASK)
    }

    def "displays reasonable error message when plugin filename does not comply with plugin id naming rules"() {
        given:
        enablePrecompiledPluginsInBuildSrc()
        pluginWithSampleTask("buildSrc/src/main/groovy/foo.bar.m%y-plugin.gradle")

        when:
        fails("help")

        then:
        failureCauseContains("plugin id 'foo.bar.m%y-plugin' is invalid")
    }

    def "can apply a precompiled script plugin by id that applies another precompiled script plugin by id"() {
        given:
        enablePrecompiledPluginsInBuildSrc()

        file("buildSrc/src/main/groovy/plugins/foo.gradle") << """
            plugins {
                id 'base'
            }

            logger.lifecycle "foo script plugin applied"
        """

        file("buildSrc/src/main/groovy/plugins/bar.gradle") << """
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

        when:
        succeeds("clean")

        then:
        outputContains("bar script plugin applied")
        outputContains("foo script plugin applied")
    }

    def "can apply multiple plugins within a precompiled script plugin"() {
        given:
        enablePrecompiledPluginsInBuildSrc()

        file("buildSrc/src/main/groovy/child1.gradle") << "println 'child1 applied'"
        file("buildSrc/src/main/groovy/child2.gradle") << "println 'child2 applied'"
        file("buildSrc/src/main/groovy/parent.gradle") << """
            plugins {
                id 'child1'
                id 'child2'
            }
            println 'parent applied'
        """

        buildFile << """
            plugins {
                id 'parent'
            }
        """

        when:
        succeeds('help')

        then:
        outputContains('child1 applied')
        outputContains('child2 applied')
        outputContains('parent applied')
    }

    def "fails the build with help message for plugin spec with version"() {
        given:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/foo.gradle") << """
            plugins {
                id 'some-plugin' version '42.0'
            }
        """

        buildFile << """
            plugins {
                id 'foo'
            }
        """

        when:
        fails("help")

        then:
        failureDescriptionContains("Invalid plugin request [id: 'some-plugin', version: '42.0']. Plugin requests from precompiled scripts must not include a version number. Please remove the version from the offending request and make sure the module containing the requested plugin 'some-plugin' is an implementation dependency")
    }

    def "can use classes from project sources"() {
        given:
        enablePrecompiledPluginsInBuildSrc()

        file("buildSrc/src/main/java/foo/bar/JavaClass.java") << """
            package foo.bar;
            public class JavaClass {}
        """
        file("buildSrc/src/main/groovy/fizz/buzz/GroovyClass.groovy") << """
            package fizz.buzz;
            class GroovyClass {}
        """

        file("buildSrc/src/main/groovy/foo.gradle") << """
            import foo.bar.JavaClass
            import fizz.buzz.GroovyClass
            println JavaClass
            println GroovyClass
            $REGISTER_SAMPLE_TASK
        """

        buildFile << """
            plugins {
                id 'foo'
            }
        """

        when:
        succeeds(SAMPLE_TASK)

        then:
        outputContains('class foo.bar.JavaClass')
        outputContains('class fizz.buzz.GroovyClass')
    }

    def "can use classes from project dependencies"() {
        given:
        file("buildSrc/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
            ${mavenCentralRepository()}

            dependencies {
                implementation("org.apache.commons:commons-lang3:3.4")
            }
        """

        file("buildSrc/src/main/groovy/plugins/foo.gradle") << """
            import org.apache.commons.lang3.StringUtils
            println StringUtils
            println StringUtils.capitalize('test')
            $REGISTER_SAMPLE_TASK
        """

        buildFile << """
            plugins {
                id 'foo'
            }
        """

        when:
        succeeds(SAMPLE_TASK)

        then:
        outputContains('class org.apache.commons.lang3.StringUtils')
        outputContains('Test')
    }

    def "can apply configuration in a precompiled script plugin to the current project"() {
        given:
        enablePrecompiledPluginsInBuildSrc()
        file("buildSrc/src/main/groovy/plugins/foo.gradle") << """
            sourceSets.main.java.srcDir 'src'
        """

        file("src/Foo.java") << """
            public class Foo { }
        """

        buildFile << """
            plugins {
                id 'java'
                id 'foo'
            }
        """

        when:
        succeeds("classes")

        then:
        executedAndNotSkipped(":compileJava")
        file("build/classes/java/main/Foo.class").exists()
    }

    def "can apply and configure a plugin in a precompiled script plugin"() {
        given:
        enablePrecompiledPluginsInBuildSrc()

        file("buildSrc/src/main/groovy/foo.gradle") << """
            plugins {
                id 'java'
            }

            sourceSets.main.java.srcDir 'src'
        """

        file("src/Foo.java") << """
            public class Foo { }
        """

        buildFile << """
            plugins {
                id 'foo'
            }
        """

        when:
        succeeds("classes")

        then:
        executedAndNotSkipped(":compileJava")
        file("build/classes/java/main/Foo.class").exists()
    }

    def "can add tasks in a precompiled script plugin"() {
        given:
        enablePrecompiledPluginsInBuildSrc()

        file("buildSrc/src/main/groovy/plugins/foo.gradle") << """
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

        when:
        succeeds("doSomething")

        then:
        outputContains("from foo plugin")
        outputContains("from main build script")
    }

    def "can use Gradle API classes directly in precompiled script plugin"() {
        given:
        enablePrecompiledPluginsInBuildSrc()

        file("buildSrc/src/main/groovy/plugins/test-plugin.gradle") << """
            class TestTask extends DefaultTask {
                @TaskAction
                void run() {
                    println 'from custom task'
                }
            }

            task testTask(type: TestTask)
        """

        buildFile << """
            plugins {
                id 'test-plugin'
            }
        """

        when:
        succeeds("testTask")

        then:
        outputContains("from custom task")
    }

    @IgnoreIf({ GradleContextualExecuter.embedded })
    // Requires a Gradle distribution on the test-under-test classpath, but gradleApi() does not offer the full distribution
    def "can write tests for precompiled script plugins"() {
        given:
        pluginWithSampleTask("src/main/groovy/test-plugin.gradle")

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
                id 'groovy-gradle-plugin'
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useSpock()
                    }
                }
            }
        """

        when:
        succeeds('build')

        then:
        executedAndNotSkipped(':test')
    }

    def "precompiled script plugins tasks are cached and relocatable"() {
        given:
        def cacheDir = createDir("cache-dir")

        def firstDir = createDir("first-location")
        firstDir.file("settings.gradle") << """
            rootProject.name = "test"
            buildCache {
                local {
                    directory = file("../${cacheDir.name}")
                }
            }
        """
        firstDir.file("buildSrc/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
        firstDir.file("buildSrc/src/main/groovy/my-plugin.gradle") << """
            println 'my-plugin applied'
        """
        firstDir.file("build.gradle") << """
            plugins {
                id 'my-plugin'
            }
        """
        def secondDir = createDir("second-location")
        firstDir.copyTo(secondDir)
        def cachedTasks = [
            ":buildSrc:extractPluginRequests",
            ":buildSrc:generatePluginAdapters",
            ":buildSrc:compileJava",
            ":buildSrc:compileGroovyPlugins"
        ]

        when:
        result = executer.inDirectory(firstDir).withTasks('help').withArgument("--build-cache").run()

        then:
        outputContains('my-plugin applied')
        cachedTasks.forEach {
            result.assertTaskExecuted(it)
        }

        when:
        result = executer.inDirectory(secondDir).withTasks('help').withArgument("--build-cache").run()

        then:
        outputContains('my-plugin applied')
        cachedTasks.forEach {
            result.assertOutputContains("$it FROM-CACHE")
        }

        when:
        secondDir.file("buildSrc/src/main/groovy/my-plugin.gradle") << """
            println 'content changed'
        """
        result = executer.inDirectory(secondDir).withTasks('help').withArgument("--build-cache").run()

        then:
        outputContains('my-plugin applied')
        outputContains('content changed')
    }

    def "a change in project sources invalidates build cache"() {
        given:
        def cacheDir = createDir("cache-dir")
        settingsFile << """
            rootProject.name = "test"
            buildCache {
                local {
                    directory = file("${cacheDir.name}")
                }
            }
        """

        file("src/main/java/foo/bar/JavaClass.java") << """
            package foo.bar;
            public class JavaClass {}
        """

        file("src/main/groovy/foo.gradle") << """
            import foo.bar.JavaClass
            $REGISTER_SAMPLE_TASK
        """

        buildFile << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """

        when:
        withBuildCache().succeeds('classes')

        file('buildSrc/build').forceDeleteDir()
        file("src/main/java/foo/bar/JavaClass.java").delete()

        then:
        withBuildCache().fails('classes')
        executed(':compileGroovyPlugins')
        outputContains(':compileGroovyPlugins FAILED')
    }

    def "can apply precompiled Groovy script plugin from Kotlin script"() {
        when:
        enablePrecompiledPluginsInBuildSrc()
        pluginWithSampleTask("buildSrc/src/main/groovy/plugins/foo.gradle")

        buildKotlinFile << """
            plugins {
                foo
            }
        """

        then:
        succeeds(SAMPLE_TASK)
    }

    def "should not allow precompiled plugin to conflict with core plugin"() {
        given:
        enablePrecompiledPluginsInBuildSrc()

        file("buildSrc/src/main/groovy/plugins/java.gradle") << ""

        when:
        def failure = fails "help"

        then:
        failure.assertHasCause("The precompiled plugin (${'src/main/groovy/plugins/java.gradle'.replace("/", File.separator)}) conflicts with the core plugin 'java'. Rename your plugin.\n\n"
            + "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/custom_plugins.html#sec:precompiled_plugins for more details.")
    }

    def "should not allow precompiled plugin to have org.gradle prefix"() {
        given:
        enablePrecompiledPluginsInBuildSrc()

        file("buildSrc/src/main/groovy/plugins/${pluginName}.gradle") << ""

        when:
        fails "help"

        then:
        failure.assertHasCause("The precompiled plugin (${"src/main/groovy/plugins/${pluginName}.gradle".replace("/", File.separator)}) cannot start with 'org.gradle'.\n\n"
            + "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/custom_plugins.html#sec:precompiled_plugins for more details.")

        where:
        pluginName << ["org.gradle.my-plugin", "org.gradle"]
    }

    private String packagePrecompiledPlugin(String pluginFile, String pluginContent = REGISTER_SAMPLE_TASK) {
        Map<String, String> plugins = [:]
        plugins.putAt(pluginFile, pluginContent)
        return packagePrecompiledPlugins(plugins)
    }

    private String packagePrecompiledPlugins(Map<String, String> pluginToContent) {
        TestFile pluginsDir = file(UUID.randomUUID().toString())
        pluginsDir.mkdir()
        pluginToContent.each { pluginFile, pluginContent ->
            pluginsDir.file("src/main/groovy/$pluginFile").setText(pluginContent)
        }
        pluginsDir.file("build.gradle").setText("""
            plugins {
                id 'groovy-gradle-plugin'
            }
        """)
        pluginsDir.file("settings.gradle").setText("""
            rootProject.name = 'plugins'
        """)

        executer.inDirectory(pluginsDir).withTasks("jar").run()
            .assertNotOutput("No valid plugin descriptors were found in META-INF/gradle-plugins")
        def pluginJar = pluginsDir.file("build/libs/plugins.jar").assertExists()
        def movedJar = file('plugins.jar')
        pluginJar.renameTo(movedJar)
        pluginsDir.forceDeleteDir()
        return movedJar.name
    }

    private void enablePrecompiledPluginsInBuildSrc() {
        file("buildSrc/build.gradle") << """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """
    }

    private void pluginWithSampleTask(String pluginPath) {
        file(pluginPath) << REGISTER_SAMPLE_TASK
    }

}
