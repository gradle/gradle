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

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.junit.junit4.AbstractJUnit4SuitesIntegrationTest
import org.gradle.util.internal.VersionNumber

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE
import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT3_VERSION

@TargetCoverage({ JUNIT_VINTAGE })
class JUnitVintageSuitesIntegrationTest extends AbstractJUnit4SuitesIntegrationTest implements JUnitVintageMultiVersionTest {
    @Override
    boolean supportsSuiteOutput() {
        // Suite output events are not correctly reported until version 5.9.0.  See https://github.com/junit-team/junit5/pull/2985.
        return VersionNumber.parse(version) >= VersionNumber.parse("5.9.0")
    }

    @Override
    String getTestFrameworkJUnit3Dependencies() {
        return """
            testCompileOnly 'junit:junit:${LATEST_JUNIT3_VERSION}'
            testRuntimeOnly 'org.junit.vintage:junit-vintage-engine:${version}'
            testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
        """
    }
}
