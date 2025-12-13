/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.initialization.StartParameterBuildOptions

class ConfigurationCacheReadOnlyIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    public static final String CONFIGURATION_CACHE_DISABLED_READ_ONLY_REASON = "Configuration cache disabled as cache is in read-only mode."

    def "should not create an entry on a cache miss if in read-only mode"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        settingsFile << ""

        when:
        configurationCacheRun("help", ENABLE_READ_ONLY_CACHE)

        then:
        configurationCache.assertNoConfigurationCache()

        postBuildOutputContains(CONFIGURATION_CACHE_DISABLED_READ_ONLY_REASON)
    }

    def "should not create an entry on a cache miss when using #options if in read-only mode"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        settingsFile << ""

        when:
        configurationCacheRun("help")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("help", *options, ENABLE_READ_ONLY_CACHE)

        then:
        configurationCache.assertNoConfigurationCache()

        postBuildOutputContains(CONFIGURATION_CACHE_DISABLED_READ_ONLY_REASON)

        where:
        options << [
            ["--write-locks"],
            ["--update-locks", "*:*"],
            ["--refresh-dependencies"],
            ["-D${StartParameterBuildOptions.ConfigurationCacheRecreateOption.PROPERTY_NAME}=true"]
        ]
    }

    def "should load an entry on a cache hit if in read-only mode"() {
        def configurationCache = newConfigurationCacheFixture()

        given:
        settingsFile << ""

        when:
        configurationCacheRun("help")

        then:
        configurationCache.assertStateStored()
        outputDoesNotContain("Read-only Configuration Cache is an incubating feature.")

        when:
        configurationCacheRun("help", ENABLE_READ_ONLY_CACHE)

        then:
        configurationCache.assertStateLoaded()
        outputContains("Read-only Configuration Cache is an incubating feature.")
    }

    def "should be able to disable read-only CC via command-line and get a cache hit"() {
        def configurationCache = newConfigurationCacheFixture()
        def disableReadOnlyCache = "-D${StartParameterBuildOptions.ConfigurationCacheReadOnlyOption.PROPERTY_NAME}=false"

        given:
        settingsFile << ""
        file("gradle.properties") << """
            org.gradle.configuration-cache.read-only=true
        """

        when:
        configurationCacheRun("help")

        then:
        configurationCache.assertNoConfigurationCache()

        when:
        configurationCacheRun("help", disableReadOnlyCache)

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("help")

        then:
        configurationCache.assertStateLoaded()
    }

    def "problems are reported and but do not fail the build when in read-only mode"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        buildFile """
            tasks.register("broken") {
                doLast {
                    println("project = " + project)
                }
            }
        """

        when:
        configurationCacheRun 'broken', ENABLE_READ_ONLY_CACHE

        then:
        configurationCache.assertNoConfigurationCache()
        postBuildOutputContains(CONFIGURATION_CACHE_DISABLED_READ_ONLY_REASON)

        problems.assertResultConsoleSummaryHasNoProblems(result)
        problems.htmlReport(result).assertContents {
            totalProblemsCount = 1
            withProblem("invocation of 'Task.project' at execution time is unsupported")
        }
    }

    def "should not fail on CC problems while evaluating lazy properties in Groovy in read-only mode"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
        class TaskWithLazyProperty extends DefaultTask {
            private String value
            @Input
            String getLazyValue() {
                if (value == null) {
                    value = project.name
                }
                return value
            }
        }
        tasks.register("lazy", TaskWithLazyProperty) { task ->
           doLast {
               println("Value is " + task.lazyValue)
           }
        }
        """

        when:
        configurationCacheRun("lazy", ENABLE_READ_ONLY_CACHE)

        then:
        configurationCache.assertNoConfigurationCache()
        result.assertTaskExecuted(":lazy")
    }

    def "should not fail on CC problems while evaluating lazy properties in Kotlin in read-only mode"() {
        def configurationCache = newConfigurationCacheFixture()

        buildKotlinFile """
        abstract class TaskWithLazyProperty: DefaultTask() {
            @get:Input
            val lazyValue: String by lazy {
                this.project.name
            }
        }
        tasks.register("lazy", TaskWithLazyProperty::class) {
            doLast {
                println("Value is " + lazyValue)
            }
        }
        """

        when:
        configurationCacheRun("lazy", ENABLE_READ_ONLY_CACHE)

        then:
        configurationCache.assertNoConfigurationCache()
        result.assertTaskExecuted(":lazy")
    }
}
