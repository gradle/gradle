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

abstract class AbstractNativeLibraryDependenciesIntegrationTest extends AbstractNativeProductionComponentDependenciesIntegrationTest {
    @ToBeFixedForConfigurationCache(bottomSpecs = ['CppLibraryDependenciesIntegrationTest'])
    def "can define api dependencies on component"() {
        given:
        createDirs("lib")
        settingsFile << 'include "lib"'
        makeComponentWithLibrary()
        buildFile << """
            ${componentUnderTestDsl} { c ->
                c.dependencies {
                    api project(':lib')
                }
            }
        """

        when:
        run(assembleDevBinaryTask)

        then:
        result.assertTasksExecuted(libDebugTasks, assembleDevBinaryTasks, assembleDevBinaryTask)
    }
}
