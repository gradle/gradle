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

package org.gradle.testing.junit.platform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION

class JUnitPlatformIntegrationSpec extends AbstractIntegrationSpec {
    def setup() {
        executer.noExtraLogging()
        buildScriptWithJupiterDependencies("""
            test {
                useJUnitPlatform()
            }
        """)
    }

    def buildScriptWithJupiterDependencies(script, String version = LATEST_JUPITER_VERSION) {
        buildScript("""
            apply plugin: 'java'

            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${version}'
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
            }
            $script
        """)
    }

    void createSimpleJupiterTest() {
        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;

            public class JUnitJupiterTest {
                @Test
                public void ok() { }
            }
            '''
    }

    void createSimpleJupiterTests() {
        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Tag;
            import org.junit.jupiter.api.Test;

            public class JUnitJupiterTest {
                @Test
                @Tag("good")
                public void good() { }

                @Test
                @Tag("bad")
                public void bad() { }
            }
            '''
    }
}
