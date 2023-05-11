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

package org.gradle.testing.junit.jupiter

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

trait JUnitJupiterMultiVersionTest {
    AbstractTestingMultiVersionIntegrationTest.BuildScriptConfiguration getBuildScriptConfiguration() {
        return new JUnitJupiterBuildScriptConfiguration()
    }

    AbstractTestingMultiVersionIntegrationTest.TestSourceConfiguration getTestSourceConfiguration() {
        return new JUnitJupiterTestSourceConfiguration()
    }

    static class JUnitJupiterBuildScriptConfiguration implements AbstractTestingMultiVersionIntegrationTest.BuildScriptConfiguration {
        String configureTestFramework = "useJUnitPlatform()"

        @Override
        String getTestFrameworkDependencies(String sourceSet) {
            return """
                ${configurationFor(sourceSet, 'implementation')} 'org.junit.jupiter:junit-jupiter-api:${MultiVersionIntegrationSpec.version}'
                ${configurationFor(sourceSet, 'runtimeOnly')} 'org.junit.jupiter:junit-jupiter-engine:${MultiVersionIntegrationSpec.version}'
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
    }

    static class JUnitJupiterTestSourceConfiguration implements AbstractTestingMultiVersionIntegrationTest.TestSourceConfiguration {
        @Override
        String getTestFrameworkImports() {
            return """
                import org.junit.jupiter.api.*;
                import org.junit.jupiter.api.extension.*;
                import static org.junit.jupiter.api.Assertions.*;
                import static org.junit.jupiter.api.Assumptions.*;
            """.stripIndent()
        }

        @Override
        String getBeforeClassAnnotation() {
            return "@BeforeAll"
        }

        @Override
        String getAfterClassAnnotation() {
            return "@AfterAll"
        }

        @Override
        String getBeforeTestAnnotation() {
            return "@BeforeEach"
        }

        @Override
        String getAfterTestAnnotation() {
            return "@AfterEach"
        }

        @Override
        String getRunOrExtendWithAnnotation(String runOrExtendWithClasses) {
            return "@ExtendWith({ ${runOrExtendWithClasses} })"
        }

        @Override
        String maybeParentheses(String methodName) {
            return "${methodName}()"
        }

        @Override
        String getIgnoreOrDisabledAnnotation() {
            return "@Disabled"
        }
    }
}
