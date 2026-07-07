/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.JdkVersionTestPreconditions
import spock.lang.Issue

/**
 * Corner-case coverage for the #22879 Kotlin script-scrubbing behavior: mutable shared state,
 * lazy self-cycles, working services at execution, settings/init/precompiled script plugins.
 */
@Issue("https://github.com/gradle/gradle/issues/22879")
@Requires(JdkVersionTestPreconditions.KotlinSupportedJdk)
class ConfigurationCacheScriptCaptureCornerCasesIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = new ConfigurationCacheFixture(this)

    def "mutable script state is shared across task actions and survives via reference identity"() {
        given:
        buildKotlinFile """
            val log = mutableListOf<String>()
            tasks.register("t") {
                doLast { log.add("first") }
                doLast { log.add("second"); println("RESULT: " + log) }
            }
        """

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT: [first, second]")
        }
    }

    def "unevaluated lazy that reads another script val works at execution"() {
        given:
        buildKotlinFile """
            val base = "hi"
            val derived: String by lazy { base.uppercase() }
            tasks.register("t") { doLast { println("RESULT: " + derived) } }
        """

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT: HI")
        }
    }

    def "lazy whose initializer touches the build model is resolved at store and cached"() {
        given:
        // Kotlin's lazy {} is Serializable and its writeReplace() forces the value; the config
        // cache honors that, so the lazy is resolved during the store — on the live project,
        // before any task runs — and the resolved value is what's cached. No execution-time failure.
        buildKotlinFile """
            val dir: String by lazy { projectDir.name }
            tasks.register("t") { doLast { println("RESULT: " + dir) } }
        """

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT: " + testDirectory.name)
        }
    }

    def "a captured function that reads the build model at execution fails gracefully"() {
        given:
        // Unlike a kotlin `by lazy` (forced at store via writeReplace), a stored function defers the
        // model access to execution, where it runs against the scrubbed script.
        buildKotlinFile """
            val readDir: () -> String = { projectDir.name }
            tasks.register("t") { doLast { println("RESULT: " + readDir()) } }
        """

        when:
        configurationCacheFails "t"

        then:
        failure.assertHasCause("Invocation of 'getProjectDir' references a Project object from a Kotlin script lambda at execution time, which is unsupported with the configuration cache.")
    }

    def "script logger works at execution"() {
        given:
        buildKotlinFile """
            tasks.register("t") { doLast { logger.lifecycle("RESULT: from-logger") } }
        """

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT: from-logger")
        }
    }

    def "capturing a Project-typed script val fails gracefully when used at execution"() {
        given:
        buildKotlinFile """
            val p = project
            tasks.register("t") { doLast { println("RESULT: " + p.name) } }
        """

        when:
        configurationCacheFails "t"

        then:
        failure.assertHasCause("Invocation of 'getName' references a Project object from a Kotlin script lambda at execution time, which is unsupported with the configuration cache.")
    }

    def "task action defined in a settings script can capture settings-script state"() {
        given:
        file("settings.gradle.kts") << """
            val s = "settings-val"
            gradle.rootProject {
                tasks.register("t") { doLast { println("RESULT: " + s) } }
            }
        """

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT: settings-val")
        }
    }

    def "task action defined in an init script can capture init-script state"() {
        given:
        file("settings.gradle.kts") << ""
        def initScript = file("init.gradle.kts")
        initScript << """
            val i = "init-val"
            rootProject {
                tasks.register("t") { doLast { println("RESULT: " + i) } }
            }
        """
        executer.beforeExecute { withArgument("-I").withArgument(initScript.absolutePath) }

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT: init-val")
        }
    }

    def "root and subproject scripts are scrubbed independently"() {
        given:
        settingsFile << "include('sub')"
        buildKotlinFile """
            val where = "root"
            tasks.register("t") { doLast { println("RESULT-root: " + where) } }
        """
        file("sub/build.gradle.kts") << """
            val where = "sub"
            tasks.register("t") { doLast { println("RESULT-sub: " + where) } }
        """

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT-root: root")
            outputContains("RESULT-sub: sub")
        }
    }

    def "PluginAware access from a build-script task action fails gracefully"() {
        given:
        buildKotlinFile """
            tasks.register("t") { doLast { println(pluginManager) } }
        """

        when:
        configurationCacheFails "t"

        then:
        failure.assertHasCause("Invocation of 'getPluginManager' references a Project object from a Kotlin script lambda at execution time, which is unsupported with the configuration cache.")
    }

    def "accessing buildscript from a build-script task action fails gracefully"() {
        given:
        buildKotlinFile """
            tasks.register("t") { doLast { println(buildscript.sourceFile) } }
        """

        when:
        configurationCacheFails "t"

        then:
        failure.assertHasCause("Invocation of 'getScriptHandler' references a KotlinScriptHost object from a Kotlin script lambda at execution time, which is unsupported with the configuration cache.")
    }

    def "accessing initscript from an init-script task action fails gracefully"() {
        given:
        file("settings.gradle.kts") << ""
        def initScript = file("init.gradle.kts")
        initScript << """
            rootProject {
                tasks.register("t") { doLast { println(initscript.sourceFile) } }
            }
        """
        executer.beforeExecute { withArgument("-I").withArgument(initScript.absolutePath) }

        when:
        configurationCacheFails "t"

        then:
        failure.assertHasCause("Invocation of 'getScriptHandler' references a KotlinScriptHost object from a Kotlin script lambda at execution time, which is unsupported with the configuration cache.")
    }

    def "init script file() keeps its base dir under the configuration cache"() {
        given:
        // resolve() routes through the init script's own file(), so this exercises the script's
        // FileOperations (rooted at the init script's dir), not the Project's file() that a bare
        // file() call inside rootProject { } would bind to.
        def initScript = file("gradle/my-init.gradle.kts")
        initScript << """
            fun resolve(path: String) = file(path)
            rootProject {
                tasks.register("checkDir") { doLast { println("RESOLVED: " + resolve("marker").absolutePath) } }
            }
        """
        settingsFile << ""
        executer.beforeExecute { withArgument("-I").withArgument(initScript.absolutePath) }

        expect: "the stored FileOperations carries the init script's base dir, so file() resolves against it on store and reuse"
        2.times {
            configurationCacheRun "checkDir"
            outputContains("RESOLVED: " + file("gradle/marker").absolutePath)
        }
    }

    def "task action defined in a precompiled script plugin can capture script state"() {
        given:
        file("buildSrc/settings.gradle.kts") << ""
        file("buildSrc/build.gradle.kts") << """
            plugins { `kotlin-dsl` }
            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """
        file("buildSrc/src/main/kotlin/my-convention.gradle.kts") << """
            val greeting = "hello-from-convention"
            tasks.register("t") { doLast { println("RESULT: " + greeting) } }
        """
        buildKotlinFile """
            plugins { id("my-convention") }
        """

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT: hello-from-convention")
        }
    }
}
