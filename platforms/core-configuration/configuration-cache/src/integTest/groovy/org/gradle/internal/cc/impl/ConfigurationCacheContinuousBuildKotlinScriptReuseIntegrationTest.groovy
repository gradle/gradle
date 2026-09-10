/*
 * Copyright 2026 Gradle and contributors.
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

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

class ConfigurationCacheContinuousBuildKotlinScriptReuseIntegrationTest extends AbstractContinuousIntegrationTest {

    def setup() {
        executer.beforeExecute {
            withArgument(AbstractConfigurationCacheIntegrationTest.ENABLE_CLI_OPT)
        }
    }

    @Issue(['https://github.com/gradle/gradle/issues/34013', 'https://github.com/gradle/gradle/issues/38482'])
    def "can re-store configuration cache in continuous build when Kotlin scripts are reused"() {
        given:
        def configFile = file("config.txt")
        def inputFile = file("input.txt")
        configFile.text = ""
        inputFile.text = "original"

        // Settings-script Spec (same pattern as develocity.buildScan.publishing.onlyIf { ... })
        settingsKotlinFile << """
            val settingsSpec: Spec<Task> = Spec { true }
            gradle.beforeProject {
                tasks.configureEach {
                    onlyIf(settingsSpec)
                }
            }
        """

        // Configuration-time file read invalidates CC without changing the script source,
        // so compiled Kotlin scripts are reused from the classloading cache.
        buildKotlinFile << """
            if (layout.projectDirectory.file("config.txt").asFile.readText().isNotEmpty()) {
                println("loaded config.txt")
            }
            tasks.register("demoTask") {
                val input = layout.projectDirectory.file("input.txt")
                inputs.files(input)
                doLast {
                    println("value: " + input.asFile.readText())
                }
            }
        """

        when:
        succeeds("demoTask")

        then:
        outputContains("value: original")
        postBuildOutputContains("Configuration cache entry stored.")

        when:
        update(configFile, "changed-config")
        update(inputFile, "changed-input")

        then:
        buildTriggeredAndSucceeded()
        outputContains("loaded config.txt")
        outputContains("value: changed-input")
        postBuildOutputContains("Configuration cache entry stored.")
        outputDoesNotContain("cannot be encoded")
    }

    /**
     * Reinstates {@code ConfigurationCacheKotlinScriptReuseIntegrationTest} (#32039 / #32205)
     * with reuse driven by continuous reconfigure instead of sibling projects with identical scripts.
     * Keeps the version catalog and early {@code buildscript.classLoader} access that induce a
     * non-strict ClassLoaderScope parent.
     */
    @Requires(value = TestExecutionPreconditions.NotEmbeddedExecutor, reason = 'non-strict ClassLoader scope')
    @Issue('https://github.com/gradle/gradle/issues/32039')
    def "compiled Kotlin script with non-strict ClassLoaderScope parent can be reused across continuous reconfigure with version catalog"() {
        given: 'a version catalog'
        file('gradle/libs.versions.toml').text = '''
            [versions]
            # Deleting this line used to avoid #32039
            test = "1"

            [libraries]
        '''.stripIndent()

        and: 'settings that induce non-strict ClassLoaderScope'
        settingsFile '''
            gradle.rootProject {
                // induces non-strict ClassLoaderScope in the hierarchy
                // since this callback runs too early
                buildscript.classLoader
            }
        '''

        and: 'a Kotlin project script reused when only non-script inputs change'
        def configFile = file('config.txt')
        def inputFile = file('input.txt')
        configFile.text = ''
        inputFile.text = 'original'

        // config.txt is read at configuration time (invalidates CC without changing script source).
        // input.txt is a task input so continuous build keeps watching the FS.
        kotlinFile 'build.gradle.kts', '''
            if (layout.projectDirectory.file("config.txt").asFile.readText().isNotEmpty()) {
                println("loaded config.txt")
            }
            tasks.register("ok") {
                val input = layout.projectDirectory.file("input.txt")
                inputs.files(input)
                doLast {
                    println("value: " + input.asFile.readText())
                }
            }
        '''

        and:
        executer.withEagerClassLoaderCreationCheckDisabled()

        when:
        succeeds 'ok'

        then:
        outputContains 'value: original'
        postBuildOutputContains 'Configuration cache entry stored.'

        when: 'invalidate configuration cache without changing the script source'
        update(configFile, 'changed-config')
        update(inputFile, 'changed-input')

        then:
        buildTriggeredAndSucceeded()
        outputContains 'loaded config.txt'
        outputContains 'value: changed-input'
        postBuildOutputContains 'Configuration cache entry stored.'
        // Must not reintroduce #32039 (non-strict scope + catalog) or #34013 (CC encode failure)
        outputDoesNotContain 'cannot be encoded'
        outputDoesNotContain 'Unexpected delegating class loader'
    }
}
