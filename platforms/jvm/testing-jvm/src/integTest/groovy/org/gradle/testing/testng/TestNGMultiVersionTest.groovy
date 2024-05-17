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

package org.gradle.testing.testng

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.testing.fixture.AbstractTestingMultiVersionIntegrationTest

trait TestNGMultiVersionTest {
    AbstractTestingMultiVersionIntegrationTest.BuildScriptConfiguration getBuildScriptConfiguration() {
        return new TestNGBuildSourceConfiguration()
    }

    AbstractTestingMultiVersionIntegrationTest.TestSourceConfiguration getTestSourceConfiguration() {
        return new TestNGTestSourceConfiguration()
    }

    static class TestNGBuildSourceConfiguration implements AbstractTestingMultiVersionIntegrationTest.BuildScriptConfiguration {
        @Override
        String getTestFrameworkDependencies(String sourceSet) {
            return """
                ${configurationFor(sourceSet, 'implementation')} 'org.testng:testng:${MultiVersionIntegrationSpec.version}'
            """.stripIndent()
        }

        @Override
        String getConfigureTestFramework() {
            return "useTestNG()"
        }

        @Override
        String getIncludeCategoryOrTagConfigurationElement() {
            // TODO implement this if needed
            throw new UnsupportedOperationException()
        }

        @Override
        String getExcludeCategoryOrTagConfigurationElement() {
            // TODO implement this if needed
            throw new UnsupportedOperationException()
        }
    }

    static class TestNGTestSourceConfiguration implements AbstractTestingMultiVersionIntegrationTest.TestSourceConfiguration {
        @Override
        String getTestFrameworkImports() {
            return """
                    import org.testng.annotations.*;
               """.stripIndent()
        }

        @Override
        String getBeforeClassAnnotation() {
            return "@BeforeClass"
        }

        @Override
        String getAfterClassAnnotation() {
            return "@AfterClass"
        }

        @Override
        String getBeforeTestAnnotation() {
            return "@BeforeTest"
        }

        @Override
        String getAfterTestAnnotation() {
            return "@AfterTest"
        }

        @Override
        String getIgnoreOrDisabledAnnotation() {
            return "@Ignore"
        }

        @Override
        String getRunOrExtendWithAnnotation(String runOrExtendWithClasses) {
            // TODO implement this if needed
            throw new UnsupportedOperationException()
        }

        @Override
        String maybeParentheses(String methodName) {
            return methodName
        }
    }
}
