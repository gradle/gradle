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

package org.gradle.internal.cc.impl.isolated

import org.gradle.internal.cc.impl.fixtures.SomeToolingModel

class IsolatedProjectsToolingApiParallelModelQueryIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "intermediate model is cached and reused for nested concurrent requests"() {
        // Sleep to ensure concurrent requests for the same model catch up before the first one is finished
        withSomeToolingModelBuilderPluginInBuildSrc("""
            Thread.sleep(3000)
        """)

        settingsFile << """
            rootProject.name = "root"
        """

        buildFile << """
            plugins {
                id("my.plugin")
            }
        """

        when:
        withIsolatedProjects()
        def models = runBuildAction(new FetchCustomModelForSameProjectInParallel())

        then:
        // Ensure nested requests are all executed and not cached individually
        outputContains("Executing nested-1")
        outputContains("Executing nested-2")

        and:
        models.size == 2
        models[0].message == "It works from project :"

        and:
        fixture.assertModelStored {
            projectsConfigured(":buildSrc", ":")
            modelsCreated(SomeToolingModel, ":") // the model is built only once and reused
        }
    }
}
