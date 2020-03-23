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

class PreCompiledScriptPluginsPluginIntegrationTest extends AbstractIntegrationSpec {
    def "adds plugin metadata to extension for all script plugins"() {
        def buildSrcDir = file("buildSrc")
        def pluginDir = buildSrcDir.createDir("src/main/groovy/plugins")
        pluginDir.file("foo.gradle").createNewFile()
        pluginDir.file("bar.gradle").createNewFile()

        buildSrcDir.file("build.gradle") << """
            afterEvaluate {
                gradlePlugin.plugins.all {
                    println it.id + ": " + it.implementationClass
                }
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("foo: fooPlugin")
        outputContains("bar: barPlugin")
    }

    def "can apply a precompiled script plugin by id"() {
        def buildSrcDir = file("buildSrc")
        def pluginDir = buildSrcDir.createDir("src/main/groovy/plugins")
        def fooPlugin = pluginDir.file("foo.gradle")

        fooPlugin << """
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

    def "can apply a precompiled script plugin by id that applies another precompiled script plugin by id"() {
        def buildSrcDir = file("buildSrc")
        def pluginDir = buildSrcDir.createDir("src/main/groovy/plugins")
        def fooPlugin = pluginDir.file("foo.gradle")
        def barPlugin = pluginDir.file("bar.gradle")

        fooPlugin << """
            plugins {
                id 'base'
            }

            logger.lifecycle "foo script plugin applied"
        """

        barPlugin << """
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

    def "can apply configuration in a precompiled script plugin to the current project"() {
        def buildSrcDir = file("buildSrc")
        def pluginDir = buildSrcDir.createDir("src/main/groovy/plugins")
        def fooPlugin = pluginDir.file("foo.gradle")

        fooPlugin << """
            sourceSets.main.java.srcDir 'src'
        """

        testDirectory.createDir("src").file("Foo.java") << """
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

    def "can apply and configure a plugin in a precompiled script plugin"() {
        def buildSrcDir = file("buildSrc")
        def pluginDir = buildSrcDir.createDir("src/main/groovy/plugins")
        def fooPlugin = pluginDir.file("foo.gradle")

        fooPlugin << """
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

    def "can add tasks in a precompiled script plugin"() {
        def buildSrcDir = file("buildSrc")
        def pluginDir = buildSrcDir.createDir("src/main/groovy/plugins")
        def fooPlugin = pluginDir.file("foo.gradle")

        fooPlugin << """
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

    def "can apply precompiled Groovy script plugin from Kotlin script"() {
        when:
        def buildSrcDir = file("buildSrc")
        def pluginDir = buildSrcDir.createDir("src/main/groovy/plugins")
        def fooPlugin = pluginDir.file("foo.gradle")

        fooPlugin << """
            tasks.register("myTask") {}
        """

        buildKotlinFile << """
            plugins {
                foo
            }
        """

        then:
        succeeds("myTask")
    }
}
