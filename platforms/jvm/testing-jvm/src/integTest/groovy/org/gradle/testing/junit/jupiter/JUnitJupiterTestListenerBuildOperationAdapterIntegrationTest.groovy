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

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.testing.AbstractTestListenerBuildOperationAdapterIntegrationTest

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER
import static org.gradle.testing.fixture.JUnitCoverage.LATEST_PLATFORM_VERSION

@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterTestListenerBuildOperationAdapterIntegrationTest extends AbstractTestListenerBuildOperationAdapterIntegrationTest implements JUnitJupiterMultiVersionTest {
    boolean emitsTestClassOperations = true

    @Override
    void checkForSuiteOperations(Iterator<BuildOperationRecord> iterator, String suiteName) {
        super.checkForSuiteOperations(iterator, suiteName)
        with(iterator.next()) {
            details.testDescriptor.name == "JUnit Jupiter"
            details.testDescriptor.composite == true
        }
    }

    @Override
    void writeTestSources() {
        buildFile << """
            dependencies {
                testImplementation 'org.junit.platform:junit-platform-suite-api:${LATEST_PLATFORM_VERSION}'
                testRuntimeOnly 'org.junit.platform:junit-platform-suite-engine:${LATEST_PLATFORM_VERSION}'
            }
        """
        file('src/test/java/org/gradle/ASuite.java') << """
            package org.gradle;
            import org.junit.platform.suite.api.Suite;
            import org.junit.platform.suite.api.SelectClasses;
            @Suite
            @SelectClasses({ org.gradle.OkTest.class, org.gradle.OtherTest.class })
            public class ASuite {
                static {
                    System.out.println("suite class loaded");
                }
            }
        """
        file('src/test/java/org/gradle/OkTest.java') << """
            package org.gradle;
            import org.junit.jupiter.api.Test;
            public class OkTest {
                @Test
                public void ok() throws Exception {
                    System.err.println("This is test stderr");
                }

                @Test
                public void anotherOk() {
                    System.out.println("sys out from another test method");
                    System.err.println("sys err from another test method");
                }
            }
        """
        file('src/test/java/org/gradle/OtherTest.java') << """
            package org.gradle;
            import org.junit.jupiter.api.Test;
            public class OtherTest {
                @Test
                public void ok() throws Exception {
                    System.out.println("This is other stdout");
                }
            }
        """
    }
}
