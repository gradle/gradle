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


abstract class AbstractNativeDependenciesIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'XCTestDependenciesIntegrationTest'
    ])
    def "can define implementation dependencies on component"() {
        given:
        createDirs("lib")
        settingsFile << 'include "lib"'
        makeComponentWithLibrary()
        buildFile << """
            ${componentUnderTestDsl} { c ->
                c.dependencies {
                    implementation project(':lib')
                }
            }
        """

        when:
        run(assembleDevBinaryTask)

        then:
        result.assertTasksExecuted(libDebugTasks, assembleDevBinaryTasks, assembleDevBinaryTask)
    }

    @ToBeFixedForConfigurationCache(bottomSpecs = [
        'XCTestDependenciesIntegrationTest'
    ])
    def "can define implementation dependencies on each binary"() {
        given:
        createDirs("lib")
        settingsFile << 'include "lib"'
        makeComponentWithLibrary()
        buildFile << """
            ${componentUnderTestDsl} {
                binaries.configureEach { b ->
                    b.dependencies {
                        implementation project(':lib')
                    }
                }
            }
        """

        when:
        run(assembleDevBinaryTask)

        then:
        result.assertTasksExecuted(libDebugTasks, assembleDevBinaryTasks, assembleDevBinaryTask)
    }

    /**
     * Creates a build with the component under test in the root project and a library in the 'lib' project.
     */
    protected abstract void makeComponentWithLibrary()

    protected abstract String getComponentUnderTestDsl()

    protected abstract String getAssembleDevBinaryTask()

    protected abstract List<String> getAssembleDevBinaryTasks()

    protected abstract List<String> getLibDebugTasks()
}
