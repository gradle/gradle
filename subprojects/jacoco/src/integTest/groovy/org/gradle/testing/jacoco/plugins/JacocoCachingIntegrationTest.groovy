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
import org.gradle.integtests.fixtures.LocalTaskCacheFixture
import org.gradle.util.Requires

import static org.gradle.util.TestPrecondition.FIX_TO_WORK_ON_JAVA9

@Requires(FIX_TO_WORK_ON_JAVA9)
class JacocoCachingIntegrationTest extends AbstractIntegrationSpec implements LocalTaskCacheFixture {

    def "jacoco file results are cached"() {
        file("src/main/java/org/gradle/Class1.java") <<
            "package org.gradle; public class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
            "package org.gradle; import org.junit.Test; public class Class1Test { @Test public void someTest() { new Class1().isFoo(\"test\"); } }"
        def reportFile = file("build/reports/jacoco/test/html/index.html")

        buildFile << """
            apply plugin: "java"
            apply plugin: "jacoco"

            repositories {
                mavenCentral()
            }
            dependencies {
                testCompile 'junit:junit:4.12'
            }

            jacocoTestReport.dependsOn test

            sourceSets.test.output.classesDir = file("build/classes/test")
        """

        when:
        withTaskCache().succeeds "jacocoTestReport"
        def snapshot = reportFile.snapshot()
        then:
        nonSkippedTasks.containsAll ":test", ":jacocoTestReport"
        reportFile.assertIsFile()

        when:
        succeeds "clean"
        then:
        reportFile.assertDoesNotExist()

        when:
        withTaskCache().succeeds "jacocoTestReport"
        then:
        skippedTasks.containsAll ":test", ":jacocoTestReport"
        reportFile.assertHasNotChangedSince(snapshot)
    }
}
