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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.JdkVersionTestPreconditions
import spock.lang.Issue

/**
 * Characterization tests pinning the CURRENT (unfixed) behavior of capturing
 * script-defined variables and methods inside task-action closures/lambdas,
 * for both Groovy and Kotlin DSLs, under the configuration cache.
 *
 * These document the starting point for gradle/gradle#22879. They are expected
 * to change once the restrictions are lifted.
 */
@Issue("https://github.com/gradle/gradle/issues/22879")
class ConfigurationCacheScriptCaptureMatrixIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = new ConfigurationCacheFixture(this)

    // --- Groovy: capturing a serializable script variable already works ---

    def "groovy task action can capture a serializable script variable (#kind)"() {
        given:
        buildFile """
            def value = $init
            tasks.register('t') {
                doLast { println "RESULT: " + $access }
            }
        """

        when:
        configurationCacheRun "t"

        then:
        outputContains("RESULT: $expected")
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "t"

        then:
        outputContains("RESULT: $expected")
        configurationCache.assertStateLoaded()

        where:
        kind     | init           | access       | expected
        "String" | '"hello"'      | "value"      | "hello"
        "File"   | "file('data')" | "value.name" | "data"
    }

    // --- Groovy: calling a script-defined method fails at execution (#20126) ---

    def "groovy task action calling a script-defined method is stored but fails on execution (#kind)"() {
        given:
        buildFile """
            $methodDecl
            tasks.register('t') {
                doLast { println "RESULT: " + $call }
            }
        """

        when:
        configurationCacheFails "t"

        then:
        // The entry is stored without a config-cache problem; the failure only surfaces at
        // execution, because the script-defined method cannot be resolved on the scrubbed owner.
        failure.assertHasCause("Could not find method $missingMethod")
        outputDoesNotContain("problem was found storing the configuration cache")

        when: "the stored entry is reused on the next run"
        configurationCacheFails "t"

        then: "it is reused (so it was committed) yet still fails at execution the same way"
        outputContains("Reusing configuration cache.")
        failure.assertHasCause("Could not find method $missingMethod")

        where:
        kind           | methodDecl                                   | call             | missingMethod
        "no-project"   | 'def describe(String s) { s.toUpperCase() }' | 'describe("hi")' | "describe()"
        "uses-project" | 'def whereAmI() { projectDir.name }'         | 'whereAmI()'     | "whereAmI()"
    }

    // --- Kotlin: script-scope capture is scrubbed, retaining Project-independent state (#22879) ---

    @Requires(JdkVersionTestPreconditions.KotlinSupportedJdk)
    def "kotlin task action can capture Project-independent script state (#kind)"() {
        given:
        buildKotlinFile """
            $decl
            tasks.register("t") {
                doLast { println("RESULT: " + $use) }
            }
        """

        when:
        configurationCacheRun "t"

        then:
        outputContains("RESULT: $expected")
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "t"

        then:
        outputContains("RESULT: $expected")
        configurationCache.assertStateLoaded()

        where:
        kind            | decl                                      | use              | expected
        "val"           | 'val greeting = "hello"'                  | "greeting"       | "hello"
        "pure method"   | 'fun describe(s: String) = s.uppercase()' | 'describe("hi")' | "HI"
        "project value" | 'val n = file("data").name'               | "n"              | "data"
    }

    @Requires(JdkVersionTestPreconditions.KotlinSupportedJdk)
    def "kotlin task action can use script file operations at execution via re-resolved services"() {
        given:
        file("data/a.txt") << "a"
        buildKotlinFile """
            tasks.register("t") {
                doLast { println("RESULT: " + file("data/a.txt").readText()) }
            }
        """

        when:
        configurationCacheRun "t"

        then:
        outputContains("RESULT: a")
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "t"

        then:
        outputContains("RESULT: a")
        configurationCache.assertStateLoaded()
    }

    @Requires(JdkVersionTestPreconditions.KotlinSupportedJdk)
    def "kotlin task action touching the build model fails gracefully at execution"() {
        given:
        buildKotlinFile """
            fun whereAmI() = projectDir.name
            tasks.register("t") {
                doLast { println("RESULT: " + whereAmI()) }
            }
        """

        when:
        configurationCacheFails "t"

        then:
        // Parity with the Groovy closure behavior: a clear execution-time problem (not an NPE),
        // and the entry is discarded because of it.
        failure.assertHasCause("Invocation of 'getProjectDir' references a Project object from a Kotlin script lambda at execution time, which is unsupported with the configuration cache.")
        outputContains("Configuration cache entry discarded with 1 problem.")
    }

    // --- Kotlin: a genuine local (declared inside run { }) is not captured from the script ---

    @Requires(JdkVersionTestPreconditions.KotlinSupportedJdk)
    def "kotlin task action can capture a local declared in a run block"() {
        given:
        buildKotlinFile """
            run {
                val greeting = "hello"
                tasks.register("t") {
                    doLast { println("RESULT: " + greeting) }
                }
            }
        """

        when:
        configurationCacheRun "t"

        then:
        outputContains("RESULT: hello")
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "t"

        then:
        outputContains("RESULT: hello")
        configurationCache.assertStateLoaded()
    }
}
