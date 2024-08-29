/*
 * Copyright 2024 the original author or authors.
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

class ConfigurationCacheParallelStoreIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "failures while storing different projects are reported"() {
        given:
        def projects = (1..5).collect  {"proj$it" }
        def configurationCache = newConfigurationCacheFixture()
        settingsFile << """
            include ${projects.collect {"'${it}'" }.join(',')}
        """

        def createBuildFile = {
            file(getDefaultBuildFileName()) << """
                task t {
                    outputs.file({ -> throw new RuntimeException("\${ project.identityPath } went BOOM!") })
                }
            """
        }

        projects.each {
            createDir(it, createBuildFile)
        }

        when:
        configurationCacheFails("t")

        then:
        configurationCache.assertStateStoreFailed()
        outputContains("Configuration cache entry discarded due to serialization error.")
        failure.assertHasFailures(1)
        // we cannot assert on the build script absolute path as it is non-deterministic
        failure.assertHasLineNumber(3)
        failure.assertHasDescription("Error while saving task graph")
        projects.each {
            failure.assertHasCause("Exception while storing configuration for :$it: :$it went BOOM!")
        }
    }

    def "parallel store is enabled by default"() {
        given:
        settingsFile.createFile()

        when:
        configurationCacheRun("help", "-d")

        then:
        output.contains("[org.gradle.configurationcache] saving task graph in parallel")
        output.contains("[org.gradle.configurationcache] reading task graph in parallel")
    }

    def "parallel store may be opted out"() {
        given:
        settingsFile.createFile()

        when:
        configurationCacheRun("help", "-d", "-Dorg.gradle.configuration-cache.internal.parallel-store=false")

        then:
        // we allow disabling parallel storing it as a safety measure for builds that might be broken by parallelism
        output.contains("[org.gradle.configurationcache] saving task graph sequentially")
        // loading is still parallel
        output.contains("[org.gradle.configurationcache] reading task graph in parallel")
    }

    def "parallel load may be opted out"() {
        given:
        settingsFile.createFile()

        when:
        configurationCacheRun("help", "-d", "-Dorg.gradle.configuration-cache.internal.parallel-load=false")

        then:
        // storing is still parallel
        output.contains("[org.gradle.configurationcache] saving task graph in parallel")
        // parallel loading should always be safe, however we will (temporarily) allow disabling it for benchmarking
        output.contains("[org.gradle.configurationcache] reading task graph sequentially")
    }
}
