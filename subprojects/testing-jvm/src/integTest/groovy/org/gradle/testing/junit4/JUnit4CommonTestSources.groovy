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

import org.gradle.testing.fixture.AbstractJUnitMultiVersionIntegrationTest

import static org.gradle.util.internal.VersionNumber.*

trait JUnit4CommonTestSources {
    AbstractJUnitMultiVersionIntegrationTest.TestSourceConfiguration getTestSourceConfiguration() {
        new JUnit4TestSourceConfiguration()
    }

    static class JUnit4TestSourceConfiguration implements AbstractJUnitMultiVersionIntegrationTest.TestSourceConfiguration {
        @Override
        String getTestFrameworkImports() {
            return """
                import org.junit.*;
                import org.junit.runner.*;

                import static org.junit.Assert.*;
                ${maybeImportAssumptions}
            """.stripIndent()
        }

        private static String getMaybeImportAssumptions() {
            def thisVersion = parse(AbstractJUnitMultiVersionIntegrationTest.version as String)
            // The Assume class was only introduced in JUnit 4.4
            if (thisVersion >= parse('4.4')) {
                return """
                    import static org.junit.Assume.*;
                """.stripIndent()
            } else {
                return ""
            }
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
            return "@Before"
        }

        @Override
        String getAfterTestAnnotation() {
            return "@After"
        }

        @Override
        String getRunOrExtendWithAnnotation(String runOrExtendWithClasses) {
            return "@RunWith(${runOrExtendWithClasses})"
        }

        @Override
        String maybeParentheses(String methodName) {
            return methodName
        }

        @Override
        String getIgnoreOrDisabledAnnotation() {
            return "@Ignore"
        }
    }
}
