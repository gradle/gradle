/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec


abstract class AbstractNativeUnitTestComponentDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec  {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
        """
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'XCTestWithApplicationDependenciesIntegrationTest'
    ])
    def "can define implementation dependencies on production component"() {
        given:
        createDirs("lib")
        settingsFile << 'include "lib"'
        makeTestSuiteAndComponentWithLibrary()
        buildFile << """
            ${productionComponentDsl} { c ->
                c.dependencies {
                    implementation project(':lib')
                }
            }
        """

        when:
        run(":test")

        then:
        result.assertTasksExecuted(libDebugTasks, runTestTasks, ":test")
    }

    protected abstract void makeTestSuiteAndComponentWithLibrary()

    protected abstract String getProductionComponentDsl()

    protected abstract List<String> getLibDebugTasks()

    protected abstract List<String> getRunTestTasks()
}
