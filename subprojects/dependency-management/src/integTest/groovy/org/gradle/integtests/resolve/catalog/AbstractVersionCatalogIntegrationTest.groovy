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

package org.gradle.integtests.resolve.catalog


import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

abstract class AbstractVersionCatalogIntegrationTest extends AbstractHttpDependencyResolutionTest {

    final BuildOperationsFixture operations = new BuildOperationsFixture(executer, testDirectoryProvider)
    final ResolveTestFixture resolve = new ResolveTestFixture(buildFile)

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        settingsFile << """
            dependencyResolutionManagement {
                repositories {
                    maven {
                        url = "${mavenHttpRepo.uri}"
                    }
                }
            }
        """
        resolve.expectDefaultConfiguration("runtime")
        resolve.prepare()
        executer.withPluginRepositoryMirrorDisabled() // otherwise the plugin portal fixture doesn't work!
    }
}
