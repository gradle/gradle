/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.JdkVersionTestPreconditions

import static org.gradle.api.internal.DocumentationRegistry.BASE_URL


@Requires(JdkVersionTestPreconditions.Jdk11OrLater)
class MicronautPluginsSmokeTest extends AbstractPluginValidatingSmokeTest {
    @Override
    String getBuildScriptConfigurationForValidation() {
        """
            micronaut {
                version '${TestedVersions.micronaut}'
           }

           pluginManager.withPlugin('io.micronaut.application') {

               ${mavenCentralRepository()}

               application {
                   mainClass = 'dummy'
               }
           }
        """
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            "io.micronaut.library": Versions.of(TestedVersions.micronaut),
            "io.micronaut.application": Versions.of(TestedVersions.micronaut),
        ]
    }

    @Override
    String getChildProjectConfiguration(String testedPluginId, String version) {
        "micronaut { version '${TestedVersions.micronaut}' }"
    }

    @Override
    List<String> getChildProjectExpectedDeprecations(String testedPluginId, String version) {
        [
            "Using a Project object as a dependency notation has been deprecated. This will fail with an error in Gradle 10. " +
                "Please use the project(String) method on DependencyHandler or the createProjectDependency(String) method on DependencyFactory instead. " +
                "Consult the upgrading guide for further information: " +
                "${BASE_URL}/userguide/upgrading_version_9.html#dependency_project_notation",
            parentMethodInvocationDeprecation('micronaut'),
        ]
    }
}
