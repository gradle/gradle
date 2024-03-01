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
import org.gradle.integtests.fixtures.AvailableJavaHomes

import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUPITER_VERSION
import static org.junit.Assume.assumeNotNull

class JUnitPlatformOnJdk7IntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        def jdk7 = AvailableJavaHomes.getJdk7()
        assumeNotNull(jdk7)
        file("gradle.properties").writeProperties("org.gradle.java.installations.paths": jdk7.javaHome.canonicalPath)
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.junit.jupiter:junit-jupiter:${LATEST_JUPITER_VERSION}'
            }

            java {
                disableAutoTargetJvm()
                toolchain {
                    languageVersion = JavaLanguageVersion.of(7)
                }
            }
            test { useJUnitPlatform() }
            """
        file('src/test/java/org/gradle/JUnitJupiterTest.java') << '''
            package org.gradle;

            import org.junit.jupiter.api.Test;

            public class JUnitJupiterTest {
                @Test
                public void ok() { }
            }
            '''
    }

    def 'can forbid user to run JUnit platform on Java 7'() {
        when:
        def failure = fails('test')

        then:
        failure.assertHasCause('Running tests with JUnit platform requires a Java 8+ toolchain.')
    }
}
