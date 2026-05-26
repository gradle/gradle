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

class ExecutionTimeOnlyOptionsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    private static final String CC_REUSED = 'Reusing configuration cache.'
    private static final String CC_NOT_REUSED = 'Calculating task graph as no cached configuration is available'

    /**
     * Defines two task types in build.gradle:
     *   MyTestLikeTask: @Option(option="tests", executionTimeOnly=true)
     *   MyCustomTask : @Option(option="tests")   // config-time
     */
    private void setupTasks() {
        buildFile << """
            import org.gradle.api.tasks.options.Option

            abstract class MyTestLikeTask extends DefaultTask {
                @Input List<String> tests = []
                @Option(option = "tests", executionTimeOnly = true, description = "Test-like option")
                void setTests(List<String> tests) { this.tests = tests }
                @TaskAction
                void run() { println "MyTestLikeTask tests=" + tests }
            }

            abstract class MyCustomTask extends DefaultTask {
                @Input List<String> tests = []
                @Option(option = "tests", description = "Config-time option")
                void setTests(List<String> tests) { this.tests = tests }
                @TaskAction
                void run() { println "MyCustomTask tests=" + tests }
            }

            tasks.register('myTestLike', MyTestLikeTask)
            tasks.register('myCustom', MyCustomTask)
        """
    }

    def "smoke: tasks register and run"() {
        given:
        setupTasks()

        when:
        run "myTestLike", "--tests", "foo"

        then:
        outputContains("MyTestLikeTask tests=[foo]")
    }

    def "B1: pure executionTimeOnly task reuses CC across value changes (after warmup)"() {
        given:
        setupTasks()

        when: "cold invocation — no manifest, expected miss"
        configurationCacheRun "myTestLike", "--tests", "A"
        then:
        outputContains(CC_NOT_REUSED)

        // The number of additional invocations required before the stripped CC key matches
        // a stored entry is an implementation detail of the storage path and may be optimized
        // independently of the value-stripping feature itself. The contract under test is:
        // eventually, a follow-up invocation with a *different* --tests value must reuse CC.
        when: "warm up the manifest with one more identical run"
        configurationCacheRun "myTestLike", "--tests", "A"

        and: "invoke with a different --tests value"
        configurationCacheRun "myTestLike", "--tests", "B"

        then: "CC is reused because --tests is execution-time-only"
        outputContains(CC_REUSED)
        outputContains("MyTestLikeTask tests=[B]")
    }

    def "B2: config-time only task does not reuse CC on value change"() {
        given:
        setupTasks()
        // Drop myTestLike so the manifest stays empty
        buildFile << "tasks.named('myTestLike').configure { onlyIf { false } }\n"

        when:
        configurationCacheRun "myCustom", "--tests", "A"
        configurationCacheRun "myCustom", "--tests", "B"

        then:
        outputContains(CC_NOT_REUSED)
        outputDoesNotContain(CC_REUSED)
        outputContains("MyCustomTask tests=[B]")
    }

    def "fails loudly when invocation mixes executionTimeOnly task with a config-time task sharing the same option name"() {
        given:
        setupTasks()

        when: "warm the manifest with the mixed invocation so the third run becomes a Load that triggers validation"
        configurationCacheRun "myTestLike", "--tests", "A", "myCustom", "--tests", "7"
        configurationCacheRun "myTestLike", "--tests", "A", "myCustom", "--tests", "7"

        and: "third identical invocation hits the load path, where validation rejects the config-time --tests on myCustom"
        configurationCacheFails(
            "myTestLike", "--tests", "A",
            "myCustom",   "--tests", "7"
        )

        then:
        // Loud-failure contract: the build must abort with a message that identifies
        // both the offending task path and the colliding option name. The exact wording
        // of the resolution bullets is intentionally not pinned so reword-only changes
        // don't break this test.
        failure.assertHasDescription("Configuration cache entry cannot be reused")
        failure.assertHasErrorOutput("':myCustom'")
        failure.assertHasErrorOutput("'--tests'")
    }

    def "B6: cold start requires two-build warmup before CC reuse"() {
        given:
        setupTasks()

        when: "Inv 1 — no manifest"
        configurationCacheRun "myTestLike", "--tests", "A"
        then:
        outputContains(CC_NOT_REUSED)

        when: "Inv 2 — manifest exists but stored entry was raw-keyed"
        configurationCacheRun "myTestLike", "--tests", "A"
        then:
        outputContains(CC_NOT_REUSED)

        when: "Inv 3 — stripped key matches stored entry"
        configurationCacheRun "myTestLike", "--tests", "A"
        then:
        outputContains(CC_REUSED)
    }

    def "B7: removing the executionTimeOnly task from the build rewrites the manifest empty"() {
        given:
        setupTasks()

        when: "Warm up first"
        configurationCacheRun "myTestLike", "--tests", "A"
        configurationCacheRun "myTestLike", "--tests", "A"
        configurationCacheRun "myTestLike", "--tests", "B"

        then:
        outputContains(CC_REUSED)

        when: "Remove MyTestLikeTask and re-configure"
        buildFile.text = """
            import org.gradle.api.tasks.options.Option
            abstract class MyCustomTask extends DefaultTask {
                @Input List<String> tests = []
                @Option(option = "tests", description = "Config-time option")
                void setTests(List<String> tests) { this.tests = tests }
                @TaskAction
                void run() { println "MyCustomTask tests=" + tests }
            }
            tasks.register('myCustom', MyCustomTask)
        """

        and: "Run with only the config-time task, twice"
        configurationCacheRun "myCustom", "--tests", "7"
        configurationCacheRun "myCustom", "--tests", "8"

        then:
        outputDoesNotContain(CC_REUSED)
        outputContains("MyCustomTask tests=[8]")
    }

    def "B8: deleting the configuration cache directory forces cold-start warmup again"() {
        given:
        setupTasks()

        when: "warm to CC reuse"
        configurationCacheRun "myTestLike", "--tests", "A"
        configurationCacheRun "myTestLike", "--tests", "A"
        configurationCacheRun "myTestLike", "--tests", "A"

        then:
        outputContains(CC_REUSED)

        when: "delete the configuration cache directory, re-run"
        file('.gradle/configuration-cache').deleteDir()
        configurationCacheRun "myTestLike", "--tests", "A"
        then:
        outputContains(CC_NOT_REUSED)
    }

    private void setupCompositeWithTestLikeTaskInIncludedBuild() {
        settingsFile << "includeBuild('app')\n"
        file('app/settings.gradle') << ''
        file('app/build.gradle') << """
            import org.gradle.api.tasks.options.Option

            abstract class MyTestLikeTask extends DefaultTask {
                @Input List<String> tests = []
                @Option(option = "tests", executionTimeOnly = true, description = "Test-like option")
                void setTests(List<String> tests) { this.tests = tests }
                @TaskAction
                void run() { println "MyTestLikeTask tests=" + tests }
            }

            tasks.register('myTestLike', MyTestLikeTask)
        """
    }

    def "B9: v1 composite-build limitation — root invocation of an included-build's executionTimeOnly task does not reuse CC across --tests changes"() {
        given:
        setupCompositeWithTestLikeTaskInIncludedBuild()

        when: "warmup invocations"
        configurationCacheRun ":app:myTestLike", "--tests", "A"
        configurationCacheRun ":app:myTestLike", "--tests", "A"

        and: "third invocation with a different --tests value"
        configurationCacheRun ":app:myTestLike", "--tests", "B"

        then: "v1 does not optimize composite — every value change misses"
        outputContains(CC_NOT_REUSED)
        outputDoesNotContain(CC_REUSED)
        outputContains("MyTestLikeTask tests=[B]")
    }
}
