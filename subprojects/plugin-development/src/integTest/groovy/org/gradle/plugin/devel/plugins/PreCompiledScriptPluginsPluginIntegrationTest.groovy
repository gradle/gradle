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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.plugin.devel.internal.precompiled.PreCompiledScriptPluginsPlugin

class PreCompiledScriptPluginsPluginIntegrationTest extends AbstractIntegrationSpec {

    private static final String SAMPLE_TASK = "sampleTask"

    def "adds plugin metadata to extension for all script plugins"() {
        def buildSrcDir = file("buildSrc")
        def pluginDir = buildSrcDir.createDir("src/main/groovy/plugins")
        pluginDir.file("foo.gradle").createNewFile()
        pluginDir.file("bar.gradle").createNewFile()

        buildSrcDir.file("build.gradle") << """
            apply plugin: ${PreCompiledScriptPluginsPlugin.name}
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

    def "can apply a precompiled script plugin by id"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")

        pluginDir.file("foo.gradle") << """
            plugins {
                id 'base'
            }

            logger.lifecycle "foo script plugin applied"
        """

        file("buildSrc/build.gradle") << """
            apply plugin: ${PreCompiledScriptPluginsPlugin.name}
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

    def "can use kebab-case in plugin id"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/")
        pluginWithSampleTask(pluginDir, "my-plugin.gradle")

        file("buildSrc/build.gradle") << """
            apply plugin: ${PreCompiledScriptPluginsPlugin.name}
        """

        buildFile << """
            plugins {
                id 'my-plugin'
            }
        """

        expect:
        succeeds(SAMPLE_TASK)
    }

    def "plugin without package can declare fully qualified id in file name"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/")
        pluginWithSampleTask(pluginDir, "foo.bar.my-plugin.gradle")

        file("buildSrc/build.gradle") << """
            apply plugin: ${PreCompiledScriptPluginsPlugin.name}
        """

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
        pluginWithSampleTask(pluginDir, "foo.bar.m%y-plugin.gradle")

        file("buildSrc/build.gradle") << """
            apply plugin: ${PreCompiledScriptPluginsPlugin.name}
        """

        expect:
        fails("help")
        failureCauseContains("plugin id 'foo.bar.m%y-plugin' is invalid")
    }

    /*def "plugin package is reflected in plugin id"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/foo/bar/")

        pluginDir.file("my-plugin.gradle") << """
            tasks.register("myTask") {}
        """

        buildFile << """
            plugins {
                id 'foo.bar.my-plugin'
            }
        """

        expect:
        succeeds("myTask")
    }*/

    def "can apply a precompiled script plugin by id that applies another precompiled script plugin by id"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")
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

        file("buildSrc/build.gradle") << """
            apply plugin: ${PreCompiledScriptPluginsPlugin.name}
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

    def "can apply configuration in a precompiled script plugin to the current project"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")

        pluginDir.file("foo.gradle") << """
            sourceSets.main.java.srcDir 'src'
        """

        createDir("src").file("Foo.java") << """
            public class Foo { }
        """

        file("buildSrc/build.gradle") << """
            apply plugin: ${PreCompiledScriptPluginsPlugin.name}
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

    def "can apply and configure a plugin in a precompiled script plugin"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")

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

        file("buildSrc/build.gradle") << """
            apply plugin: ${PreCompiledScriptPluginsPlugin.name}
        """

        expect:
        succeeds("classes")

        and:
        executedAndNotSkipped(":compileJava")

        and:
        file("build/classes/java/main/Foo.class").exists()
    }

    def "can add tasks in a precompiled script plugin"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")

        pluginDir.file("foo.gradle") << """
            task doSomething {
                doFirst { println "from foo plugin" }
            }
        """

        file("buildSrc/build.gradle") << """
            apply plugin: ${PreCompiledScriptPluginsPlugin.name}
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

    def "can apply precompiled Groovy script plugin from Kotlin script"() {
        def pluginDir = createDir("buildSrc/src/main/groovy/plugins")
        pluginWithSampleTask(pluginDir, "foo.gradle")

        file("buildSrc/build.gradle") << """
            apply plugin: ${PreCompiledScriptPluginsPlugin.name}
        """

        buildKotlinFile << """
            plugins {
                foo
            }
        """

        expect:
        succeeds(SAMPLE_TASK)
    }

    private static void pluginWithSampleTask(TestFile pluginDir, String pluginFile) {
        pluginDir.file(pluginFile) << """
            tasks.register("$SAMPLE_TASK") {}
        """
    }
}
