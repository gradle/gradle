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

package org.gradle.testing.junit.junit4

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.gradle.testing.fixture.JUnitCoverage

trait JUnit4MultiVersionTest extends JUnit4CommonTestSources {
    AbstractTestingMultiVersionIntegrationTest.BuildScriptConfiguration getBuildScriptConfiguration() {
        return new JUnit4BuildScriptConfiguration()
    }

    AbstractTestingMultiVersionIntegrationTest.TestSourceConfiguration getTestSourceConfiguration() {
        new JUnit4TestSourceConfiguration(AbstractTestingMultiVersionIntegrationTest.version as String)
    }

    static class JUnit4BuildScriptConfiguration implements AbstractTestingMultiVersionIntegrationTest.BuildScriptConfiguration {
        String configureTestFramework = "useJUnit()"

        @Override
        String getTestFrameworkDependencies(String sourceSet) {
            if (MultiVersionIntegrationSpec.version.startsWith("3")) {
                // We support compiling classes with JUnit 3, but we require JUnit 4 at runtime (which is backwards compatible).
                // The runtime dependency would not be necessary if using test suites, which would automatically inject the runtime
                // dependency if not specified.
                return """
                    ${configurationFor(sourceSet, 'compileOnly')} 'junit:junit:${MultiVersionIntegrationSpec.version}'
                    ${configurationFor(sourceSet, 'runtimeOnly')} 'junit:junit:${JUnitCoverage.LATEST_JUNIT4_VERSION}'
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
