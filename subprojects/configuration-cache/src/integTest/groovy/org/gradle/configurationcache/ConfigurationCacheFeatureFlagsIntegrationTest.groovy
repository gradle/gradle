/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.internal.FeaturePreviews
import org.gradle.configurationcache.fixtures.ExternalProcessFixture

import static org.gradle.configurationcache.fixtures.ExternalProcessFixture.exec

class ConfigurationCacheFeatureFlagsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "toggling feature flag with system property invalidates cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        ExternalProcessFixture execFixture = new ExternalProcessFixture(testDirectory)

        def snippets = exec().groovy.newSnippets(execFixture)

        buildFile("""
            ${snippets.imports}

            ${snippets.body}

            task check {}
        """)

        when:
        configurationCacheRun("check")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheFails("-D${FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE.systemPropertyName}=true", "check")

        then:
        problems.assertFailureHasProblems(failure) {
            withProblem("Build file 'build.gradle': external process started")
        }
    }

    def "feature flag state is restored when running from cache"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile("""
            plugins {
                id("groovy")
            }

            dependencies {
                implementation localGroovy()
            }
        """)

        settingsFile("""
            enableFeaturePreview("${FeaturePreviews.Feature.GROOVY_COMPILATION_AVOIDANCE.name()}")
        """)

        file("src/main/groovy/Main.groovy") << """
            class Main {}
        """

        when:
        configurationCacheRun("compileGroovy")

        then:
        configurationCache.assertStateStored()
        outputContains("Groovy compilation avoidance is an incubating feature")

        when:
        configurationCacheRun("compileGroovy", "--rerun-tasks")

        then:
        configurationCache.assertStateLoaded()
        outputContains("Groovy compilation avoidance is an incubating feature")
    }
}
