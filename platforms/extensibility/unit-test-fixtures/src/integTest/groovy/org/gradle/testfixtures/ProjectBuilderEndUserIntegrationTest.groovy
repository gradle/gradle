/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testfixtures

import org.gradle.api.internal.tasks.testing.worker.TestWorker
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.SupportedJavaVersions
import org.gradle.internal.jvm.SupportedJavaVersionsExpectations
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import org.hamcrest.Matcher
import org.intellij.lang.annotations.Language
import org.junit.Assume

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
class ProjectBuilderEndUserIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def setup() {
        buildFile << """
            plugins {
                id("java-gradle-plugin")
                id("groovy")
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useSpock()
                    }
                }
            }
        """

        executer.requireOwnGradleUserHomeDir(
            "ProjectBuilder tests run against the generated Gradle API Jar, and that Jar gets cached once per distribution in the user home. " +
                "If we make changes to Gradle between test executions, we continue to use the cached API jar. " +
                "This ensures we re-generate that API jar for each test execution"
        )
    }

    def "worker tmp dir system property is set"() {
        given:
        withTest("""
            expect:
            System.getProperty("${TestWorker.WORKER_TMPDIR_SYS_PROPERTY}") != null
        """)

        when:
        succeeds("test")

        then:
        testPassed()
    }

    def "project builder has correctly set working directory"() {
        given:
        def workerTmpDir = file("build/tmp/test/work")
        withTest("""
            when:
            def gradleUserHome = ProjectBuilder.builder().build().gradle.gradleUserHomeDir

            then:
            gradleUserHome.toPath().startsWith("${TextUtil.normaliseFileSeparators(workerTmpDir.absolutePath)}")
            gradleUserHome.absolutePath.endsWith("userHome")
        """)

        when:
        succeeds('test')

        then:
        testPassed()
    }

    @Requires(IntegTestPreconditions.UnsupportedDaemonJavaHomeAvailable)
    def "using project builder on unsupported java version fails"() {
        Assume.assumeTrue("Gradle can execute tests", jdk.javaVersionMajor >= SupportedJavaVersions.MINIMUM_WORKER_JAVA_VERSION)

        given:
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jdk.javaVersionMajor})
                }
            }
        """
        withTest("""
            expect:
            ProjectBuilder.builder().build()
        """)

        when:
        withInstallations(jdk)
        fails('test')

        then:
        testFailed(containsString(SupportedJavaVersionsExpectations.getIncompatibleDaemonJvmVersionErrorMessage("Gradle", jdk.javaVersionMajor)))

        where:
        jdk << AvailableJavaHomes.getUnsupportedDaemonJdks()
    }

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "using project builder on deprecated java version emits a deprecation warning"() {
        given:
        withTest("""
            expect:
            ProjectBuilder.builder().build()
        """)

        when:
        succeeds('test')

        then:
        testPassed()
        assertTestStdout(containsString(SupportedJavaVersionsExpectations.expectedDaemonDeprecationWarning))
    }

    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "using project builder on non-deprecated java version does not emit a deprecation warning"() {
        given:
        withTest("""
            expect:
            ProjectBuilder.builder().build()
        """)

        when:
        succeeds('test')

        then:
        testPassed()
        assertTestStdout(not(containsString(SupportedJavaVersionsExpectations.expectedDaemonDeprecationWarning)))
    }

    def "can call deprecation logger"() {
        given:
        withTest("""
            when:
            def project = ProjectBuilder.builder().build()
            project.with {
                //noinspection UnnecessaryQualifiedReference
                org.gradle.internal.deprecation.DeprecationLogger
                    .deprecate("test deprecation").willBecomeAnErrorInNextMajorGradleVersion().undocumented()
                    .nagUser()
            }

            then:
            noExceptionThrown()
        """)

        when:
        succeeds('test')

        then:
        testPassed()
        assertTestStdout(not(containsString("DeprecationLogger has not been initialized")))
    }

    void withTest(@Language("Groovy") String body) {
        file("src/test/groovy/Test.groovy") << """
            import spock.lang.Specification
            import org.gradle.testfixtures.ProjectBuilder

            class Test extends Specification {
                def "test"() {
                    $body
                }
            }
        """
    }

    void testPassed() {
        def results = new DefaultTestExecutionResult(testDirectory)
        results.assertTestClassesExecuted('Test')
        results.testClass('Test').assertTestPassed('test')
    }

    void testFailed(Matcher<String> matcher) {
        def results = new DefaultTestExecutionResult(testDirectory)
        results.assertTestClassesExecuted('Test')
        results.testClass('Test').assertTestFailed('test', matcher)
    }

    void assertTestStdout(Matcher<String> matcher) {
        def results = new DefaultTestExecutionResult(testDirectory)
        results.testClass('Test').assertStdout(matcher)
    }
}
