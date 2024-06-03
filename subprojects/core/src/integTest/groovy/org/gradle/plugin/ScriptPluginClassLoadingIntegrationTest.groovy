/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

import static org.gradle.util.Matchers.containsText

class ScriptPluginClassLoadingIntegrationTest extends AbstractIntegrationSpec {

    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Issue("https://issues.gradle.org/browse/GRADLE-3069")
    def "second level and beyond script plugins have same base class loader scope as caller"() {
        when:
        file("buildSrc/src/main/java/pkg/Thing.java") << """
            package pkg;
            public class Thing {
              public String getMessage() { return "hello"; }
            }
        """

        file("plugin1.gradle") << """
            task sayMessageFrom1 { doLast { println new pkg.Thing().getMessage() } }
            apply from: 'plugin2.gradle'
        """

        file("plugin2.gradle") << """
            task sayMessageFrom2 { doLast { println new pkg.Thing().getMessage() } }
            apply from: 'plugin3.gradle'
        """

        file("plugin3.gradle") << """
            task sayMessageFrom3 { doLast { println new pkg.Thing().getMessage() } }
        """

        buildScript "apply from: 'plugin1.gradle'"

        then:
        succeeds "sayMessageFrom1", "sayMessageFrom2", "sayMessageFrom3"
        output.contains "hello"
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3079")
    def "methods defined in script are available to used script plugins"() {
        given:
        buildScript """
          def addTask(project) {
            project.tasks.create("hello").doLast { println "hello from method" }
          }

          apply from: "script.gradle"
        """

        file("script.gradle") << "addTask(project)"

        when:
        succeeds "hello"

        then:
        output.contains "hello from method"
    }

    def "methods defined in script plugin are not inherited by applied script plugin"() {
        given:
        buildScript """
            apply from: "script1.gradle"
        """

        file("script1.gradle") << """
            def someMethod() {}
            apply from: "script2.gradle"
        """

        file("script2.gradle") << """
            someMethod()
        """

        when:
        fails "help"

        then:
        failure.assertHasFileName("Script '${file("script2.gradle").absolutePath}'")
        failure.assertThatCause(containsText("Could not find method someMethod()"))
    }

    def "methods defined in settings script are not inherited by scripts"() {
        given:
        settingsFile << """
            def someMethod() {}
        """

        buildScript """
            someMethod()
        """

        when:
        fails "help"

        then:
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertThatCause(containsText("Could not find method someMethod()"))
    }

    def "methods defined in init script are not inherited by scripts"() {
        given:
        file("init.gradle") << """
            def someMethod() {}
        """

        buildScript """
            someMethod()
        """

        when:
        fails "help", "-I", file("init.gradle").absolutePath

        then:
        failure.assertHasFileName("Build file '${buildFile.absolutePath}'")
        failure.assertThatCause(containsText("Could not find method someMethod()"))
    }

    @Requires(
        value = IntegTestPreconditions.NotIsolatedProjects,
        reason = "Exercises IP incompatible behavior"
    )
    def "methods defined in a build script are visible to scripts applied to sub projects"() {
        given:
        settingsFile << "include 'sub'"

        buildScript """
            def someMethod() {
                println "from some method"
            }
        """

        file("sub/build.gradle") << "apply from: 'script.gradle'"
        file("sub/script.gradle") << "someMethod()"

        when:
        run "help"

        then:
        output.contains("from some method")
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-3082")
    def "can use apply block syntax to apply multiple scripts"() {
        given:
        buildScript """
          apply {
            from "script1.gradle"
            from "script2.gradle"
          }
        """

        file("script1.gradle") << "task hello1 { doLast { println 'hello from script1' } }"
        file("script2.gradle") << "task hello2 { doLast { println 'hello from script2' } }"

        when:
        succeeds "hello1", "hello2"

        then:
        output.contains "hello from script1"
        output.contains "hello from script2"
    }

    def "separate classloaders are used when using multi apply syntax"() {
        given:
        buildScript """
          apply {
            from "script1.gradle"
            from "script2.gradle"
          }
        """

        file("script1.gradle") << "class Foo {}"
        file("script2.gradle") << "new Foo()"

        when:
        fails "help"

        then:
        failure.assertHasFileName("Script '${file("script2.gradle").absolutePath}'")
        failure.assertThatCause(containsText("unable to resolve class Foo"))
    }

    @ToBeFixedForIsolatedProjects(because = "Investigate")
    def "script plugin buildscript does not affect client"() {
        given:
        def jar = file("plugin.jar")
        pluginBuilder.addPlugin("project.task('hello')")
        pluginBuilder.publishTo(executer, jar)

        settingsFile << "include 'sub'"

        buildScript """
            apply from: "script.gradle"

            try {
                getClass().classLoader.loadClass(pluginClass.name)
                assert false
            } catch (ClassNotFoundException ignore) {
                println "not in root"
            } finally {
                getClass().classLoader.close()
            }

        """

        file("script.gradle") << """
          buildscript {
            dependencies { classpath files("plugin.jar") }
          }

          apply plugin: org.gradle.test.TestPlugin
          ext.pluginClass = org.gradle.test.TestPlugin
        """

        file("sub/build.gradle") << """
            try {
                getClass().classLoader.loadClass(pluginClass.name)
                assert false
            } catch (ClassNotFoundException ignore) {
                println "not in sub"
            } finally {
                getClass().classLoader.close()
            }
        """

        when:
        succeeds "hello"

        then:
        output.contains "not in root"
        output.contains "not in sub"
    }

    def "script plugin cannot access classes added by buildscript in applying script"() {
        given:
        def jar = file("plugin.jar")
        pluginBuilder.addPlugin("project.task('hello')")
        pluginBuilder.publishTo(executer, jar)

        buildScript """
            buildscript {
                dependencies { classpath files("plugin.jar") }
            }

            apply plugin: org.gradle.test.TestPlugin
            ext.pluginClass = org.gradle.test.TestPlugin

            apply from: "script.gradle"
        """

        file("script.gradle") << """
            try {
                getClass().classLoader.loadClass(pluginClass.name)
                assert false
            } catch (ClassNotFoundException ignore) {
                println "not in script"
            } finally {
                getClass().classLoader.close()
            }
        """

        when:
        succeeds "hello"

        then:
        output.contains "not in script"
    }

    def "second level script plugin cannot access classes added by buildscript in applying script"() {
        given:
        def jar = file("plugin.jar")
        pluginBuilder.addPlugin("project.task('hello')")
        pluginBuilder.publishTo(executer, jar)

        buildScript """
            apply from: "script1.gradle"
        """

        file("script1.gradle") << """
            buildscript {
                dependencies { classpath files("plugin.jar") }
            }

            apply plugin: org.gradle.test.TestPlugin
            ext.pluginClass = org.gradle.test.TestPlugin

            apply from: "script2.gradle"
        """

        file("script2.gradle") << """
            try {
                getClass().classLoader.loadClass(pluginClass.name)
                assert false
            } catch (ClassNotFoundException ignore) {
                println "not in script"
            } finally {
                getClass().classLoader.close()
            }
        """

        when:
        succeeds "hello"

        then:
        output.contains "not in script"
    }

    def "Can apply a script plugin to the buildscript block"() {
        given:
        def jar = file("plugin.jar")
        pluginBuilder.addPlugin("project.task('hello')")
        pluginBuilder.publishTo(executer, jar)

        buildScript """
            apply from: 'foo.gradle'
        """

        file("foo.gradle") << """
            buildscript {
                apply from: "repositories.gradle", to: buildscript
                dependencies { classpath files("plugin.jar") }
            }
        """

        file("repositories.gradle") << """
        """

        expect:
        succeeds "help"
    }
}
