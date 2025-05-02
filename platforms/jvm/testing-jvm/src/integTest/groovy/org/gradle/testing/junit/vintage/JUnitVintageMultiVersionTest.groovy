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

package org.gradle.testing.junit.vintage

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest
import org.gradle.testing.junit.junit4.JUnit4CommonTestSources

import static org.gradle.testing.fixture.JUnitCoverage.*

trait JUnitVintageMultiVersionTest extends JUnit4CommonTestSources {
    AbstractTestingMultiVersionIntegrationTest.BuildScriptConfiguration getBuildScriptConfiguration() {
        return new JUnitVintageBuildScriptConfiguration()
    }

    AbstractTestingMultiVersionIntegrationTest.TestSourceConfiguration getTestSourceConfiguration() {
        new JUnit4TestSourceConfiguration(MultiVersionIntegrationSpec.version)
    }

    static class JUnitVintageBuildScriptConfiguration implements AbstractTestingMultiVersionIntegrationTest.BuildScriptConfiguration {
        String configureTestFramework = "useJUnitPlatform()"

        @Override
        String getTestFrameworkDependencies(String sourceSet) {
            return """
                ${configurationFor(sourceSet, 'compileOnly')} 'junit:junit:${LATEST_JUNIT4_VERSION}'
                ${configurationFor(sourceSet, 'runtimeOnly')} 'org.junit.vintage:junit-vintage-engine:${MultiVersionIntegrationSpec.version}'
                ${configurationFor(sourceSet, 'runtimeOnly')} 'org.junit.platform:junit-platform-launcher'
            """
        }

        @Override
        String getIncludeCategoryOrTagConfigurationElement() {
            return "includeTags"
        }

        @Override
        String getExcludeCategoryOrTagConfigurationElement() {
            return "excludeTags"
        }

        @Override
        boolean supportsJavaVersion(int javaVersion) {
            return javaVersion >= 8
        }
    }
}
