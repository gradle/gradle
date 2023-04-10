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

package org.gradle.testing.junitplatform

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.testing.fixture.AbstractJUnitMultiVersionIntegrationTest

trait JUnitJupiterMultiVersionTest {
    AbstractJUnitMultiVersionIntegrationTest.BuildScriptConfiguration getBuildScriptConfiguration() {
        return new JUnitJupiterBuildScriptConfiguration()
    }

    static class JUnitJupiterBuildScriptConfiguration implements AbstractJUnitMultiVersionIntegrationTest.BuildScriptConfiguration {
        String testFrameworkConfigurationMethod = "useJUnitPlatform()"

        List<String> getTestFrameworkImplementationDependencies() {
            return ["org.junit.jupiter:junit-jupiter-api:${dependencyVersion}"]
        }

        List<String> getTestFrameworkRuntimeDependencies() {
            return ["org.junit.jupiter:junit-jupiter-engine:${dependencyVersion}"]
        }

        static String getDependencyVersion() {
            return MultiVersionIntegrationSpec.version.toString().substring("Jupiter:".length())
        }

        @Override
        String getIncludeCategoryOrTagConfigurationElement() {
            return "includeTags"
        }

        @Override
        String getExcludeCategoryOrTagConfigurationElement() {
            return "excludeTags"
        }
    }
}
