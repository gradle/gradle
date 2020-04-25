/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testkit.scenario

import org.gradle.testkit.scenario.collection.BuildCacheScenario


class BuildCacheScenarioIntegrationTest extends AbstractGradleScenarioIntegrationTest {

    def "can run build cache scenario"() {

        given:
        def scenario = BuildCacheScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(underTestWorkspace)
            .withTaskPaths(underTestTaskPath)

        when:
        def result = scenario.run()

        then:
        result.ofStep(BuildCacheScenario.Steps.CLEAN_BUILD)
        result.ofStep(BuildCacheScenario.Steps.FROM_CACHE_BUILD)
        result.ofStep(BuildCacheScenario.Steps.FROM_CACHE_RELOCATED_BUILD)
    }

    def "can run build cache scenario without relocatability test"() {

        given:
        def scenario = BuildCacheScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace(underTestWorkspace)
            .withTaskPaths(underTestTaskPath)
            .withoutRelocatabilityTest()

        when:
        def result = scenario.run()

        then:
        result.ofStep(BuildCacheScenario.Steps.CLEAN_BUILD)
        result.ofStep(BuildCacheScenario.Steps.FROM_CACHE_BUILD)

        and:
        try {
            result.ofStep(BuildCacheScenario.Steps.FROM_CACHE_RELOCATED_BUILD)
        } catch (IllegalArgumentException ex) {
            ex.message == "No result for scenario step named '${BuildCacheScenario.Steps.FROM_CACHE_RELOCATED_BUILD}'"
        }
    }

    def "from-cache step fails when task is not cacheable"() {

        given:
        def scenario = BuildCacheScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace { root ->
                underTestWorkspace.execute(root)
                def buildFile = new File(root, 'build.gradle')
                buildFile.text = buildFile.text.replaceAll("@Cacheable", "")
            }
            .withTaskPaths(underTestTaskPath)


        when:
        scenario.run()

        then:
        def ex = thrown(AssertionError)
        ex.message == "Step '${BuildCacheScenario.Steps.FROM_CACHE_BUILD}': expected task ':underTest' to be FROM_CACHE but was SUCCESS"
    }

    def "relocatability step fails when task is not relocatable"() {

        given:
        def scenario = BuildCacheScenario.create()
            .withRunnerFactory(underTestRunnerFactory)
            .withBaseDirectory(underTestBaseDirectory)
            .withWorkspace { root ->
                underTestWorkspace.execute(root)
                def buildFile = new File(root, 'build.gradle')
                buildFile.text = buildFile.text.replaceAll(
                    "PathSensitivity.NONE",
                    "PathSensitivity.ABSOLUTE"
                )
            }
            .withTaskPaths(underTestTaskPath)

        when:
        scenario.run()

        then:
        def ex = thrown(AssertionError)
        ex.message == "Step '${BuildCacheScenario.Steps.FROM_CACHE_RELOCATED_BUILD}': expected task ':underTest' to be FROM_CACHE but was SUCCESS"
    }
}
