/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.testing

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.gradle.testing.fixture.JUnitMultiVersionIntegrationSpec
import org.gradle.util.Matchers
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE_JUPITER
import static org.gradle.testing.fixture.JUnitCoverage.LATEST_PLATFORM_VERSION

@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE_JUPITER })
class TestEnvironmentIntegrationTest extends JUnitMultiVersionIntegrationSpec {
    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    def canRunTestsWithCustomSystemClassLoader() {
        when:
        run 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JUnitTest')
        result.testClass('org.gradle.JUnitTest').assertTestPassed('mySystemClassLoaderIsUsed')
    }

    def canRunTestsReferencingSlf4jWithCustomSystemClassLoader() {
        when:
        run 'test'

        then:
        def testResults = new DefaultTestExecutionResult(testDirectory)
        testResults.assertTestClassesExecuted('org.gradle.TestUsingSlf4j')
        with(testResults.testClass('org.gradle.TestUsingSlf4j')) {
            assertTestPassed('mySystemClassLoaderIsUsed')
            assertStderr(Matchers.containsText("ERROR via slf4j"))
            assertStderr(Matchers.containsText("WARN via slf4j"))
            assertStderr(Matchers.containsText("INFO via slf4j"))
        }
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def canRunTestsReferencingSlf4jWithModularJava() {
        given:
        if(isJupiter() && TestPrecondition.JDK14_OR_LATER.fulfilled) {
            // Otherwise it throws exception:
            // java.lang.IllegalAccessError: class org.junit.platform.launcher.core.LauncherFactory (in unnamed module @0x2f2a5b2d)
            // cannot access class org.junit.platform.commons.util.Preconditions (in module org.junit.platform.commons) because module org.junit.platform.commons does not export org.junit.platform.commons.util to unnamed module @0x2f2a5b2d
            //
            // See https://github.com/openjdk/skara/pull/66 for details of this workaround
            buildFile.text = buildFile.text.replace('dependencies {', """dependencies {
                testRuntimeOnly 'org.junit.platform:junit-platform-launcher:${LATEST_PLATFORM_VERSION}'
                """)
        }

        when:
        run 'test'

        then:
        def testResults = new DefaultTestExecutionResult(testDirectory)
        testResults.assertTestClassesExecuted('org.gradle.example.TestUsingSlf4j')
        with(testResults.testClass('org.gradle.example.TestUsingSlf4j')) {
            assertTestPassed('testModular')
            assertStderr(Matchers.containsText("ERROR via slf4j"))
            assertStderr(Matchers.containsText("WARN via slf4j"))
            assertStderr(Matchers.containsText("INFO via slf4j"))
        }
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER) //hangs on Java9
    def canRunTestsWithCustomSystemClassLoaderAndJavaAgent() {
        ignoreWhenJUnitPlatform()

        when:
        run 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JUnitTest')
        result.testClass('org.gradle.JUnitTest').assertTestPassed('mySystemClassLoaderIsUsed')
    }

    def canRunTestsWithCustomSecurityManager() {
        executer
                .withArgument("-Porg.gradle.java.installations.paths=${AvailableJavaHomes.getAvailableJvms().collect { it.javaHome.absolutePath }.join(",")}")
                .withToolchainDetectionEnabled()

        when:
        run 'test'

        then:
        def result = new DefaultTestExecutionResult(testDirectory)
        result.assertTestClassesExecuted('org.gradle.JUnitTest')
        result.testClass('org.gradle.JUnitTest').assertTestPassed('mySecurityManagerIsUsed')
    }
}
