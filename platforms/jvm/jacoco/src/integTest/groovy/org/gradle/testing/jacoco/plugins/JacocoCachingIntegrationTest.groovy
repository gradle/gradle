/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

class JacocoCachingIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)
    private final TestFile reportFile = file("build/reports/jacoco/test/html/index.html")

    def setup() {
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()

        buildFile << """
            jacocoTestReport.dependsOn test

            sourceSets.test.java.destinationDirectory.set(file("build/classes/test"))

            test {
                jacoco {
                    classDumpDir = file("\$buildDir/tmp/jacoco/classpathdumps")
                }
            }
        """
    }

    def "jacoco file results are cached"() {
        when:
        withBuildCache().run "test", "jacocoTestReport", "jacocoTestCoverageVerification"
        def snapshot = reportFile.snapshot()
        then:
        executedAndNotSkipped ":test", ":jacocoTestReport", ":jacocoTestCoverageVerification"
        reportFile.assertIsFile()

        when:
        succeeds "clean"
        then:
        reportFile.assertDoesNotExist()

        when:
        withBuildCache().run "jacocoTestReport", "jacocoTestCoverageVerification"
        then:
        skipped ":test", ":jacocoTestReport", ":jacocoTestCoverageVerification"
        reportFile.assertContentsHaveNotChangedSince(snapshot)
    }

    def "jacoco file results are not cached when sharing output with another task"() {
        javaProjectUnderTest.writeIntegrationTestSourceFiles()
        buildFile << """
            integrationTest.jacoco {
                destinationFile = test.jacoco.destinationFile
            }
        """
        when:
        withBuildCache().run "jacocoTestReport"
        then:
        executedAndNotSkipped ":test", ":jacocoTestReport"
        reportFile.assertIsFile()

        when:
        succeeds "clean"
        then:
        reportFile.assertDoesNotExist()

        when:
        withBuildCache().run("integrationTest", "jacocoIntegrationTestReport")
        and:
        withBuildCache().run "jacocoTestReport"
        then:
        executedAndNotSkipped ":test", ":jacocoTestReport"
    }

    def "test execution is cached with different gradle user home"() {
        when:
        withBuildCache().run "test", "jacocoTestReport"
        def snapshot = reportFile.snapshot()
        then:
        executedAndNotSkipped ":test", ":jacocoTestReport"
        reportFile.assertIsFile()

        when:
        succeeds "clean"
        then:
        reportFile.assertDoesNotExist()

        when:
        executer.requireOwnGradleUserHomeDir()
        withBuildCache().run "jacocoTestReport"
        then:
        skipped ":test", ":jacocoTestReport"
        reportFile.assertContentsHaveNotChangedSince(snapshot)
    }

    def "test is cached when jacoco is disabled"() {
        buildFile << """
            test {
                jacoco {
                    enabled = false
                }
            }
        """
        when:
        withBuildCache().run "test"
        then:
        executedAndNotSkipped ":test"

        when:
        succeeds "clean"
        then:
        reportFile.assertDoesNotExist()

        when:
        executer.requireOwnGradleUserHomeDir()
        withBuildCache().run "test"
        then:
        skipped ":test"
    }
}
