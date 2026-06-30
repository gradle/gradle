/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsBuildFeatureIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "build feature reports configuration cache as implied by Isolated Projects"() {
        buildFile """
            import org.gradle.api.configuration.BuildFeatures

            def buildFeatures = gradle.services.get(BuildFeatures)
            tasks.register("something") {
                doLast {
                    println "configurationCache.requested=" + buildFeatures.configurationCache.requested.getOrNull()
                    println "configurationCache.active=" + buildFeatures.configurationCache.active.get()
                }
            }
        """

        when:
        isolatedProjectsRun "something"
        then:
        fixture.assertStateStored {
            projectConfigured(":")
        }
        outputContains("configurationCache.requested=null")
        outputContains("configurationCache.active=true")

        when:
        isolatedProjectsRun "something"
        then:
        fixture.assertStateLoaded()
        outputContains("configurationCache.requested=null")
        outputContains("configurationCache.active=true")
    }

    def "not active due to --export-keys flag, even explicitly if requested"() {
        buildFile """
            import org.gradle.api.configuration.BuildFeatures

            def buildFeatures = gradle.services.get(BuildFeatures)
            tasks.register("something") {
                doLast {
                    println "configurationCache.requested=" + buildFeatures.configurationCache.requested.getOrNull()
                    println "configurationCache.active=" + buildFeatures.configurationCache.active.get()
                    println "isolatedProjects.requested=" + buildFeatures.isolatedProjects.requested.getOrNull()
                    println "isolatedProjects.active=" + buildFeatures.isolatedProjects.active.get()
                }
            }
        """

        when:
        isolatedProjectsRun "something", "--export-keys"
        then:
        fixture.assertNoConfigurationCache()
        outputContains("configurationCache.requested=null")
        outputContains("configurationCache.active=false")
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=false")
    }

}
