/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.testing.fixture.AbstractJvmFailFastIntegrationSpec

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION

class JUnitPlatformFailFastIntegrationTest extends AbstractJvmFailFastIntegrationSpec {
    @Override
    String testAnnotationClass() {
        'org.junit.jupiter.api.Test'
    }

    @Override
    String testDependencies() {
        """
            testImplementation 'org.junit.jupiter:junit-jupiter:$LATEST_JUPITER_VERSION'
            testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
        """
    }

    @Override
    String testFrameworkConfiguration() {
        'test { useJUnitPlatform() }'
    }
}
