/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.testing.junit.junit6.vintage

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.internal.jvm.Jvm
import org.gradle.testing.AbstractTestJavaVersionIntegrationTest
import org.gradle.testing.junit.vintage.IgnoresJUnit6VintageDeprecationWarning
import org.gradle.testing.junit.vintage.JUnitVintageMultiVersionTest

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_6_VINTAGE

/**
 * Tests support for different JUnit Vintage versions across different Java versions.
 */
@TargetCoverage({ JUNIT_6_VINTAGE })
class JUnit6VintageJavaVersionIntegrationTest extends AbstractTestJavaVersionIntegrationTest implements JUnitVintageMultiVersionTest, IgnoresJUnit6VintageDeprecationWarning {
    @Override
    List<Jvm> getSupportedJvms() {
        // JUnit Vintage 6+ requires Java 17 or higher
        return AvailableJavaHomes.supportedWorkerJdks.findAll { it.javaVersionMajor >= 17 }
    }
}
