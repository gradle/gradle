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

package org.gradle.testing.junit.junit6.vintage

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.junit.AbstractJUnitTestExecutionIntegrationTest
import org.gradle.testing.junit.vintage.IgnoresJUnit6VintageDeprecationWarning
import org.gradle.testing.junit.vintage.JUnitVintageMultiVersionTest

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_6_VINTAGE
import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT4_VERSION

@TargetCoverage({ JUNIT_6_VINTAGE })
class JUnit6VintageTestExecutionIntegrationTest extends AbstractJUnitTestExecutionIntegrationTest implements JUnitVintageMultiVersionTest, IgnoresJUnit6VintageDeprecationWarning {
    @Override
    String getJUnitVersionAssertion() {
        return "assertEquals(\"${LATEST_JUNIT4_VERSION}\", new org.junit.runner.JUnitCore().getVersion());"
    }

    @Override
    String getStableEnvironmentDependencies() {
        return super.getStableEnvironmentDependencies() + """
            testImplementation 'junit:junit:${LATEST_JUNIT4_VERSION}'
        """
    }
}
