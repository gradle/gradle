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
import org.gradle.integtests.fixtures.LocalBuildCacheFixture
import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

class JacocoCachingIntegrationTest extends AbstractIntegrationSpec implements LocalBuildCacheFixture {

    private final JavaProjectUnderTest javaProjectUnderTest = new JavaProjectUnderTest(testDirectory)

    def "jacoco file results are cached"() {
        javaProjectUnderTest.writeBuildScript().writeSourceFiles()
        def reportFile = file("build/reports/jacoco/test/html/index.html")

        buildFile << """
            jacocoTestReport.dependsOn test
            
            sourceSets.test.output.classesDir = file("build/classes/test")
            
            test {
                jacoco {
                    // No caching when append is enabled
                    append = false
                    classDumpDir = file("\$buildDir/tmp/jacoco/classpathdumps")
                }
            }
        """

        when:
        withBuildCache().succeeds "jacocoTestReport"
        def snapshot = reportFile.snapshot()
        then:
        nonSkippedTasks.containsAll ":test", ":jacocoTestReport"
        reportFile.assertIsFile()

        when:
        succeeds "clean"
        then:
        reportFile.assertDoesNotExist()

        when:
        withBuildCache().succeeds "jacocoTestReport"
        then:
        skippedTasks.containsAll ":test", ":jacocoTestReport"
        reportFile.assertHasNotChangedSince(snapshot)
    }
}
