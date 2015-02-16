/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Test

@Requires(TestPrecondition.JDK7_OR_EARLIER)
@TargetVersions(['0.6.0.201210061924', '0.6.2.201302030002', '0.7.1.201405082137'])
class JacocoVersionIntegTest extends MultiVersionIntegrationSpec {

    @Test
    public void canRunVersions() {
        given:
        buildFile << """
        apply plugin: "java"
        apply plugin: "jacoco"

        repositories {
            mavenCentral()
        }

        dependencies {
            testCompile 'junit:junit:4.12'
        }
        jacoco {
            toolVersion = '$version'
        }
        """
        createTestFiles();

        when:
        succeeds('test', 'jacocoTestReport')

        then:
        def report = htmlReport()
        report.totalCoverage() == 100
        report.jacocoVersion() == version
    }

    private JacocoReportFixture htmlReport(String basedir = "build/reports/jacoco/test/html") {
        return new JacocoReportFixture(file(basedir))
    }

    private void createTestFiles() {
        file("src/main/java/org/gradle/Class1.java") <<
                "package org.gradle; public class Class1 { public boolean isFoo(Object arg) { return true; } }"
        file("src/test/java/org/gradle/Class1Test.java") <<
                "package org.gradle; import org.junit.Test; public class Class1Test { @Test public void someTest() { new Class1().isFoo(\"test\"); } }"
    }
}
