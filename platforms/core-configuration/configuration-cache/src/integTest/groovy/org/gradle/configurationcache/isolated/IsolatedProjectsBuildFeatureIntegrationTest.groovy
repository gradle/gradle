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

package org.gradle.configurationcache.isolated


class IsolatedProjectsBuildFeatureIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    def "build feature indicates requested and enabled status"() {
        buildFile """
            def buildFeatures = gradle.buildFeatures
            tasks.register("something") {
                doLast {
                    println "configurationCache.isRequested=" + buildFeatures.configurationCache.isRequested()
                    println "configurationCache.isEnabled=" + buildFeatures.configurationCache.isEnabled()
                    println "isolatedProjects.isRequested=" + buildFeatures.isolatedProjects.isRequested()
                    println "isolatedProjects.isEnabled=" + buildFeatures.isolatedProjects.isEnabled()
                }
            }
        """

        when:
        run "something"
        then:
        fixture.assertNoConfigurationCache()
        outputContains("configurationCache.isRequested=false")
        outputContains("configurationCache.isEnabled=false")
        outputContains("isolatedProjects.isRequested=false")
        outputContains("isolatedProjects.isEnabled=false")

        when:
        isolatedProjectsRun "something"
        then:
        fixture.assertStateStored {
            projectConfigured(":")
        }
        outputContains("configurationCache.isRequested=true")
        outputContains("configurationCache.isEnabled=true")
        outputContains("isolatedProjects.isRequested=true")
        outputContains("isolatedProjects.isEnabled=true")

        when:
        isolatedProjectsRun "something"
        then:
        fixture.assertStateLoaded()
        outputContains("configurationCache.isRequested=true")
        outputContains("configurationCache.isEnabled=true")
        outputContains("isolatedProjects.isRequested=true")
        outputContains("isolatedProjects.isEnabled=true")
    }

    def "disabled even if requested due to --export-keys flag"() {
        buildFile """
            def buildFeatures = gradle.buildFeatures
            tasks.register("something") {
                doLast {
                    println "configurationCache.isRequested=" + buildFeatures.configurationCache.isRequested()
                    println "configurationCache.isEnabled=" + buildFeatures.configurationCache.isEnabled()
                    println "isolatedProjects.isRequested=" + buildFeatures.isolatedProjects.isRequested()
                    println "isolatedProjects.isEnabled=" + buildFeatures.isolatedProjects.isEnabled()
                }
            }
        """

        when:
        isolatedProjectsRun "something", "--export-keys"
        then:
        fixture.assertNoConfigurationCache()
        outputContains("configurationCache.isRequested=true")
        outputContains("configurationCache.isEnabled=false")
        outputContains("isolatedProjects.isRequested=true")
        outputContains("isolatedProjects.isEnabled=false")
    }

}
