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

package org.gradle.testing.junit4

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.testing.fixture.AbstractJUnitMultiVersionIntegrationTest
import org.gradle.testing.fixture.JUnitCoverage

trait JUnit4MultiVersionTest extends JUnit4CommonTestSources {
    AbstractJUnitMultiVersionIntegrationTest.BuildScriptConfiguration getBuildScriptConfiguration() {
        return new JUnit4BuildScriptConfiguration()
    }

    static class JUnit4BuildScriptConfiguration implements AbstractJUnitMultiVersionIntegrationTest.BuildScriptConfiguration {
        String configureTestFramework = "useJUnit()"

        @Override
        String getTestFrameworkDependencies(String sourceSet) {
            if (MultiVersionIntegrationSpec.version.startsWith("3")) {
                return """
                    ${configurationFor(sourceSet, 'compileOnly')} 'junit:junit:${MultiVersionIntegrationSpec.version}'
                    ${configurationFor(sourceSet, 'runtimeOnly')} 'junit:junit:${JUnitCoverage.JUNIT_4_LATEST}'
                """
            } else {
                return """
                    ${configurationFor(sourceSet, 'implementation')} 'junit:junit:${MultiVersionIntegrationSpec.version}'
                """
            }
        }

        @Override
        String getIncludeCategoryOrTagConfigurationElement() {
            return "includeCategories"
        }

        @Override
        String getExcludeCategoryOrTagConfigurationElement() {
            return "excludeCategories"
        }
    }
}
