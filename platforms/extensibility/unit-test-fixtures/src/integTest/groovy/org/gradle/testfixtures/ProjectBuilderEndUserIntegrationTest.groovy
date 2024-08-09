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
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.GradleVersion
import org.gradle.util.internal.TextUtil
import org.hamcrest.Matcher

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
class ProjectBuilderEndUserIntegrationTest extends AbstractIntegrationSpec {

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
        testExecuted()
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
        testExecuted()
    }

    @Requires(UnitTestPreconditions.DeprecatedDaemonJdkVersion)
    def "using project builder on Java versions earlier than 17 emits a deprecation warning"() {
        given:
        withTest("""
            expect:
            ProjectBuilder.builder().build()
        """)

        when:
        succeeds('test')

        then:
        testExecuted()
        assertTestStdout(containsString("Executing Gradle on JVM versions 16 and lower has been deprecated. This will fail with an error in Gradle 9.0. Use JVM 17 or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains. Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version"))
    }

    @Requires(UnitTestPreconditions.NonDeprecatedDaemonJdkVersion)
    def "using project builder on Java versions 17 and later does not emit a deprecation warning"() {
        given:
        withTest("""
            expect:
            ProjectBuilder.builder().build()
        """)

        when:
        succeeds('test')

        then:
        testExecuted()
        assertTestStdout(not(containsString("Executing Gradle on JVM versions 16 and lower has been deprecated")))
    }

    def "can set test variables"() {
        given:
        withTest("""
            when:
            def project = ProjectBuilder.builder()
                .withEnvironmentVariable("envKey2", "testEnvValue2")
                .withSystemProperty("sysProp2", "testSysValue2")
                .withGradleProperty("gradleProp", "testValue")
                .build()
            def envProvider1 = project.providers.environmentVariable("envKey1")
            def envProvider2 = project.providers.environmentVariable("envKey2")
            def sysProvider1 = project.providers.systemProperty("sysProp1")
            def sysProvider2 = project.providers.systemProperty("sysProp2")
            def gradleProvider = project.providers.gradleProperty("gradleProp")

            then:
            envProvider1.get() == "realEnvValue1"
            envProvider2.get() == "testEnvValue2"
            sysProvider1.get() == "realSysValue1"
            sysProvider2.get() == "testSysValue2"
            gradleProvider.get() == "testValue"
        """)
        buildFile """
            tasks.test {
                environment("envKey1", "realEnvValue1")
                environment("envKey2", "realEnvValue2")
                systemProperty("sysProp1", "realSysValue1")
                systemProperty("sysProp2", "realSysValue2")
            }
        """

        when:
        succeeds('test')

        then:
        testExecuted()
    }

    void withTest(String body) {
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

    void testExecuted() {
        def results = new DefaultTestExecutionResult(testDirectory)
        results.assertTestClassesExecuted('Test')
        results.testClass('Test').assertTestsExecuted('test')
    }

    void assertTestStdout(Matcher<String> matcher) {
        def results = new DefaultTestExecutionResult(testDirectory)
        results.testClass('Test').assertStdout(matcher)
    }
}
