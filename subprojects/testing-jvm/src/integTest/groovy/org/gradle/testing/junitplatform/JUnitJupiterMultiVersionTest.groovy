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

    AbstractJUnitMultiVersionIntegrationTest.TestSourceConfiguration getTestSourceConfiguration() {
        return new JUnitJupiterTestSourceConfiguration()
    }

    String getDependencyVersion() {
        return JUnitJupiterBuildScriptConfiguration.getDependencyVersion()
    }

    static class JUnitJupiterBuildScriptConfiguration implements AbstractJUnitMultiVersionIntegrationTest.BuildScriptConfiguration {
        String configureTestFramework = "useJUnitPlatform()"

        @Override
        String getTestFrameworkDependencies() {
            return """
                testImplementation 'org.junit.jupiter:junit-jupiter-api:${dependencyVersion}'
                testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:${dependencyVersion}'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            """
        }

        static String getDependencyVersion() {
            assert MultiVersionIntegrationSpec.version.startsWith("Jupiter:")
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

    static class JUnitJupiterTestSourceConfiguration implements AbstractJUnitMultiVersionIntegrationTest.TestSourceConfiguration {
        @Override
        String getTestFrameworkImports() {
            return """
                import org.junit.jupiter.api.*;
                import static org.junit.jupiter.api.Assertions.*;
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
    }
}
