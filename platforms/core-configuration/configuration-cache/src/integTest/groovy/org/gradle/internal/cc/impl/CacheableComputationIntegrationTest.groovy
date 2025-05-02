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

import org.gradle.internal.serialization.Cached

class CacheableComputationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "cacheable computation that does not trigger execution-time deprecations"() {
        given:
        def projectName = "project1"
        settingsFile """
            enableFeaturePreview 'STABLE_CONFIGURATION_CACHE'
            rootProject.name = '$projectName'
        """

        buildFile """
        abstract class CompatibleTask extends DefaultTask {
            // accessing Task.project via a cacheable computation should not trigger execution-time checks
            private def projectName = ${cachableComputation} { project.name }
            @TaskAction
            def doIt() {
                println "project name: \${projectName.get()}"
            }
        }

        tasks.register("compatible", CompatibleTask)
        """

        // cacheable computations with CC will not produce problems
        when:
        configurationCacheRun("compatible")

        then:
        problems.assertNoProblemsSummary()

        // cacheable computations without CC should not produce access-check deprecations
        when:
        succeeds("compatible")

        then:
        result.assertTaskExecuted(":compatible")
        outputContains("project name: $projectName")

        where:
        cachableComputation << [
            "${Cached.name}.of",
            "project.providers.provider"
        ]
    }
}
