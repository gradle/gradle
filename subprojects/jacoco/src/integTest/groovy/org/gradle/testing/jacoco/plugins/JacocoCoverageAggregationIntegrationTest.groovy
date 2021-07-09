/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportFixture

class JacocoCoverageAggregationIntegrationTest extends AbstractIntegrationSpec {

    private static final JUNIT_DEPENDENCY = "org.junit.jupiter:junit-jupiter-engine:5.7.2"
    private static final AGGREGATION_TASK_NAME = "jacocoAggregatedTestReport"

    def setup() {
        writeClass("lib1", "Lib1")
        writeClass("lib2", "Lib2")
        writeClass("app", "App")
        settingsFile << """
            include("lib1", "lib2", "app")
            dependencyResolutionManagement {
                ${mavenCentralRepository()}
            }
        """
        file("lib1/build.gradle") << """
            plugins {
                id("java-library")
                id("jacoco")
            }
            ${configureJUnitPlatform()}
        """
        file("lib2/build.gradle") << """
            plugins {
                id("java-library")
                id("jacoco")
            }
            ${configureJUnitPlatform()}
        """
        file("app/build.gradle") << """
            plugins {
                id("application")
                id("jacoco")
            }
            ${configureJUnitPlatform()}
        """
    }

    @ToBeFixedForConfigurationCache(because = "outgoing variants report isn't compatible")
    def "registers code coverage data variants"() {
        given:
        projectHasIntegrationTests("app", "App")

        when:
        succeeds("app:outgoingVariants", "--variant", variant)

        then:
        outputContains("Description = ${description}")
        outputContains("- ${artifact}")

        where:
        variant                           | description                                                              | artifact
        "testCoverageElements"            | "Jacoco test coverage data variant for tests from test task."            | "build${File.separator}jacoco${File.separator}test.exec (artifactType = exec)"
        "integrationTestCoverageElements" | "Jacoco test coverage data variant for tests from integrationTest task." | "build${File.separator}jacoco${File.separator}integrationTest.exec (artifactType = exec)"
        "sourceDirectoriesElements"       | "Java source directories variant."                                       | "src${File.separator}main${File.separator}java"
    }

    @ToBeFixedForConfigurationCache(because = "outgoing variants report isn't compatible")
    def "registers jacoco coverage variant when test task is used outside of java ecosystem"() {
        given:
        file("lib1/build.gradle").setText("""
            plugins {
                id("jacoco")
            }
            tasks.register("someTest", Test)
        """)

        when:
        succeeds(":lib1:outgoingVariants", "--variant", "someTestCoverageElements")

        then:
        outputContains("Description = Jacoco test coverage data variant for tests from someTest task.")
        outputContains("- build${File.separator}jacoco${File.separator}someTest.exec (artifactType = exec)")
    }

    def "aggregates unit test results for the app and its library dependencies"() {
        given:
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", JacocoAggregatedReport)
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
        aggregatedReport.totalCoverage() == 63
    }

    def "aggregates test results for specified test categories for the app and its library dependencies into a single report"() {
        given:
        projectHasIntegrationTests(projectWithIntegrationTests, "Lib1")
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", JacocoAggregatedReport) {
                testTaskNames = ['${testsToAggregate.join("', '")}']
            }
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        for (String testName : testsToAggregate) {
            executed(":${projectWithIntegrationTests}:${testName}")
        }
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
        aggregatedReport.totalCoverage() == totalCoverage

        where:
        testsToAggregate            | totalCoverage | projectWithIntegrationTests
        ["test"]                    | 63            | "lib1"
        ["integrationTest"]         | 21            | "lib1"
        ["test", "integrationTest"] | 75            | "lib1"
        ["test"]                    | 63            | "app"
        ["integrationTest"]         | 21            | "app"
        ["test", "integrationTest"] | 75            | "app"
    }

    def "can create multiple different test report aggregations for the app and its library dependencies into a single report"() {
        given:
        projectHasIntegrationTests("lib1", "Lib1")
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
            tasks.register("aggregatedTestReport", JacocoAggregatedReport) {
                testTaskNames = ["test"]
            }
            tasks.register("aggregatedIntegrationTestReport", JacocoAggregatedReport) {
                testTaskNames = ["integrationTest"]
            }
            tasks.register("aggregatedMergedReport", JacocoAggregatedReport) {
                testTaskNames = ["test", "integrationTest"]
            }
        """

        when:
        succeeds("aggregatedTestReport")

        then:
        def testReport = htmlReport("app", "aggregatedTestReport")
        testReport.exists()
        testReport.numberOfClasses() == 3
        testReport.totalCoverage() == 63

        when:
        succeeds("aggregatedIntegrationTestReport")

        then:
        def integrationTestReport = htmlReport("app", "aggregatedIntegrationTestReport")
        integrationTestReport.exists()
        integrationTestReport.numberOfClasses() == 3
        integrationTestReport.totalCoverage() == 21

        when:
        succeeds("aggregatedMergedReport")

        then:
        def mergedReport = htmlReport("app", "aggregatedMergedReport")
        mergedReport.exists()
        mergedReport.numberOfClasses() == 3
        mergedReport.totalCoverage() == 75
    }

    def "aggregates unit test results for the app and its library dependencies transitively"() {
        given:
        file("lib1/build.gradle") << """
            dependencies {
                implementation(project(":lib2"))
            }
        """
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", JacocoAggregatedReport)
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
        aggregatedReport.totalCoverage() == 63
    }

    def "excludes external dependency classes from aggregated unit test results"() {
        given:
        file("lib1/build.gradle") << """
            dependencies {
                 implementation("commons-io:commons-io:2.6")
            }
        """
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
                implementation("junit:junit:4.13")
            }
            tasks.register("${AGGREGATION_TASK_NAME}", JacocoAggregatedReport)
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
        aggregatedReport.totalCoverage() == 63
    }

    def "aggregates test results using a dedicated aggregation subproject"() {
        given:
        settingsFile << """
            include("aggregation")
        """
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
        """
        file("aggregation/build.gradle") << """
            plugins {
                id("jacoco")
            }
            dependencies {
                jacocoAggregation(project(":app"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", JacocoAggregatedReport)
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("aggregation", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
        aggregatedReport.totalCoverage() == 63
    }

    def "handles no source directories when dedicated aggregation project applies java-base plugin"() {
        given:
        settingsFile << """
            include("aggregation")
        """
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
        """
        file("aggregation/build.gradle") << """
            plugins {
                id("jacoco")
                id("java-base")
            }
            dependencies {
                jacocoAggregation(project(":app"))
            }
            def aggregationTask = tasks.register("${AGGREGATION_TASK_NAME}", JacocoAggregatedReport)
            tasks.named("check") {
                dependsOn(aggregationTask)
            }
        """

        when:
        succeeds("check")

        then:
        executed(":aggregation:${AGGREGATION_TASK_NAME}")
        def aggregatedReport = htmlReport("aggregation", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
        aggregatedReport.totalCoverage() == 63
    }

    def "produces aggregated report given some project does not produce coverage data"() {
        given:
        settingsFile << """
            include("lib3")
        """
        file("lib3/build.gradle") << """
            plugins {
                id("jacoco")
                id("java")
            }
        """
        writeClass("lib3", "Lib3", false)
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
                implementation(project(":lib3"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", JacocoAggregatedReport)
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 4
        aggregatedReport.totalCoverage() == 47
    }

    def "produces aggregated report given dedicated aggregation project is a Java project and does not produce coverage data"() {
        given:
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
        """
        settingsFile << """
            include("aggregation")
        """
        file("aggregation/build.gradle") << """
            plugins {
                id("jacoco")
                id("java")
            }
            dependencies {
                implementation(project(":app"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", JacocoAggregatedReport)
        """
        writeClass("aggregation", "Aggregation", false)

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("aggregation", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 4
        aggregatedReport.totalCoverage() == 47
    }

    def "assemble does not execute tests"() {
        given:
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
            tasks.register("${AGGREGATION_TASK_NAME}", JacocoAggregatedReport)
        """

        when:
        succeeds("assemble")

        then:
        notExecuted(":app:test", ":lib1:test", ":lib2:test")
    }

    private JacocoReportFixture htmlReport(String project, String reportName) {
        return new JacocoReportFixture(file(project + "/build/reports/jacoco/${reportName}/html"))
    }

    private static String configureJUnitPlatform() {
        return """
            dependencies {
                testImplementation("${JUNIT_DEPENDENCY}")
            }
            tasks.named("test") {
                useJUnitPlatform()
            }
        """
    }

    private void projectHasIntegrationTests(String subproject, String className) {
        file("${subproject}/build.gradle") << """
            sourceSets {
                integrationTest {
                    compileClasspath += sourceSets.main.output
                    runtimeClasspath += sourceSets.main.output
                }
            }
            configurations {
                integrationTestImplementation {
                    extendsFrom(configurations.testImplementation)
                }
            }
            tasks.register("integrationTest", Test) {
                useJUnitPlatform()
                classpath = sourceSets.integrationTest.runtimeClasspath
                testClassesDirs = sourceSets.integrationTest.output.classesDirs
            }
        """
        writeIntegrationTest(subproject, className)
    }

    private void writeIntegrationTest(String subproject, String className) {
        file("${subproject}/src/integrationTest/java/com/example/${className}IntegrationTest.java") << """
                package com.example;
                import org.junit.jupiter.api.Test;
                class ${className}IntegrationTest {
                    @Test
                    void integrationTest${className}() {
                        new ${className}().integration();
                    }
                }
            """
    }

    private void writeClass(String subproject, String className, boolean withTest = true) {
        file("${subproject}/src/main/java/com/example/${className}.java") << """
            package com.example;
            public class ${className} {
                void unit() {
                    System.out.println("unit");
                }
                void integration() {
                    System.out.println("integration");
                }
            }
        """
        if (withTest) {
            file("${subproject}/src/test/java/com/example/${className}Test.java") << """
                package com.example;
                import org.junit.jupiter.api.Test;
                class ${className}Test {
                    @Test
                    void unitTest${className}() {
                        new ${className}().unit();
                    }
                }
            """
        }
    }
}

