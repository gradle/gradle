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

package org.gradle.testing.junit.junit5.vintage

import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testing.fixture.JUnitCoverage
import org.gradle.testing.junit.junit4.AbstractJUnit4TestEnvironmentIntegrationTest
import org.gradle.testing.junit.vintage.JUnitVintageMultiVersionTest

@TargetCoverage({ JUnitCoverage.JUNIT_5_VINTAGE })
class JUnit5VintageTestEnvironmentIntegrationTest extends AbstractJUnit4TestEnvironmentIntegrationTest implements JUnitVintageMultiVersionTest {
    @Override
    boolean isFrameworkSupportsModularJava() {
        return true
    }

    @Requires(IntegTestPreconditions.Java11HomeAvailable)
    def "can run tests with custom security manager"() {
        executer
            .withArgument("-Dorg.gradle.java.installations.paths=${AvailableJavaHomes.getAvailableJvms().collect { it.javaHome.absolutePath }.join(",")}")
            .withToolchainDetectionEnabled()

        given:
        file('src/test/java/org/gradle/JUnitTest.java') << """
            package org.gradle;

            ${testFrameworkImports}

            public class JUnitTest {
                @Test
                public void mySecurityManagerIsUsed() throws ClassNotFoundException {
                    assertTrue(System.getSecurityManager() instanceof MySecurityManager);
                    assertEquals(ClassLoader.getSystemClassLoader(), MySecurityManager.class.getClassLoader());
                }
            }
        """.stripIndent()
        file('src/test/java/org/gradle/MySecurityManager.java') << """
            package org.gradle;

            import java.security.Permission;

            public class MySecurityManager extends SecurityManager {
                public MySecurityManager() {
                    assert getClass().getName().equals(System.getProperty("java.security.manager"));
                }

                @Override
                public void checkPermission(Permission permission) {
                }
            }
        """.stripIndent()
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(11)
                }
            }
            test {
                systemProperties 'java.security.manager': 'org.gradle.MySecurityManager'
            }
        """.stripIndent()

        when:
        run 'test'

        then:
        def results = resultsFor(testDirectory)
        results.testPath('org.gradle.JUnitTest', 'mySecurityManagerIsUsed').onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
    }
}
