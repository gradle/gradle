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

    def "a top-level var is shared across task actions via an identity-preserving cell"() {
        given:
        // Capture minimization lifts the `var` into a shared kotlin.jvm.internal.Ref cell. Both actions
        // must observe each other's writes, which requires the configuration cache to deduplicate the
        // (bean) cell across the two lambdas that capture it. If it did not, each action would get its
        // own cell and the result would be "RESULT: 1".
        buildKotlinFile """
            var counter = 0
            tasks.register("t") {
                doLast { counter += 1 }
                doLast { counter += 1; println("RESULT: " + counter) }
            }
        """

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT: 2")
        }
    }

    def "an is-prefixed boolean var is lifted into a shared cell using its Kotlin accessor names"() {
        given:
        // Kotlin names an `is`-prefixed property's accessors isReady()/setReady(), not getIsReady()/
        // setIsReady(); capture minimization must use those names to recognize and lift the var. Both
        // actions must observe the shared cell, so the flag flipped by the first is seen by the second.
        buildKotlinFile """
            var isReady = false
            tasks.register("t") {
                doLast { isReady = true }
                doLast { println("RESULT: " + isReady) }
            }
        """

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT: true")
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

    def "a captured function-typed var that reads the build model at execution fails gracefully"() {
        given:
        // Like the val case above, a function-typed `var` must not be lifted: its value is a lambda that
        // captured the script, so it stays behind the scrubbed script and fails gracefully at execution.
        buildKotlinFile """
            var readDir: () -> String = { projectDir.name }
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

    def "capturing a Project-typed script val fails fast at store because the project cannot be serialized"() {
        given:
        // The task action reads only the top-level `val p`, so capture minimization lifts it out of the
        // script. `p` is the real Project, which the configuration cache cannot serialize — so the build
        // fails fast at store time. That is preferable to storing a scrubbed proxy that would only fail
        // later when the task runs.
        buildKotlinFile """
            val p = project
            tasks.register("t") { doLast { println("RESULT: " + p.name) } }
        """

        when:
        configurationCacheFails "t"

        then:
        failure.assertHasDescription("Configuration cache problems found in this build.")
        failure.assertHasErrorOutput("cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
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

    def "script state is isolated per task after cache reuse"() {
        given:
        // Each task node is serialized in its own configuration-cache isolate, so a script captured by
        // two tasks' actions deserializes to a separate scrubbed copy per task. Cross-task sharing of
        // mutable script state — which happens without the cache, where both actions run against one
        // live script instance — is therefore intentionally not preserved: relying on one task's action
        // mutating script state that another task observes is order-dependent and unsupported with the
        // configuration cache.
        buildKotlinFile """
            val shared = mutableListOf<String>()
            tasks.register("a") { doLast { shared.add("from-a") } }
            tasks.register("b") { dependsOn("a"); doLast { println("RESULT: " + shared) } }
        """

        when: "without the configuration cache, both tasks share one live script instance"
        succeeds "b"

        then:
        outputContains("RESULT: [from-a]")

        when: "with the configuration cache, each task runs against its own scrubbed script copy"
        configurationCacheRun "b"

        then: "state mutated by :a is not visible to :b — each task has its own script copy"
        outputContains("RESULT: []")
    }

    def "an unused script by-lazy is not forced because the task action does not capture the whole script"() {
        given:
        // Capture minimization: the doLast action reads only the top-level `val used` (an immutable
        // String), so it is rewritten to carry that value directly instead of the whole compiled script.
        // The script — and therefore the unused `by lazy` — is never serialized, so the lazy is not
        // forced at store time (mirroring the no-cache behavior, where an unused lazy is never evaluated).
        buildKotlinFile """
            val used = "hi"
            val neverUsed: String by lazy { println("LAZY-FORCED"); "x" }
            tasks.register("t") { doLast { println("RESULT: " + used) } }
        """

        expect:
        2.times {
            configurationCacheRun "t"
            outputContains("RESULT: hi")
            outputDoesNotContain("LAZY-FORCED")
        }
    }

}
