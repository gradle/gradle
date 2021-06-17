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
import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportFixture

class JacocoCoverageAggregationIntegrationTest extends AbstractIntegrationSpec {

    private static final AGGREGATION_TASK_NAME = "jacocoAggregatedTestReport"

    def setup() {
        writeClassWithTest("lib1", "Lib1")
        writeClassWithTest("lib2", "Lib2")
        writeClassWithTest("app", "App")
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
            ${registerOutgoingCoverageVariants()}
        """
        file("lib2/build.gradle") << """
            plugins {
                id("java-library")
                id("jacoco")
            }
            ${configureJUnitPlatform()}
            ${registerOutgoingCoverageVariants()}
        """
        file("app/build.gradle") << """
            plugins {
                id("application")
                id("jacoco")
            }
            ${configureJUnitPlatform()}
            ${registerOutgoingCoverageVariants()}
        """
    }

    def "aggregates unit test results for the app and its library dependencies"() {
        given:
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
            ${configureAggregation()}
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
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
            ${configureAggregation()}
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
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
            ${configureAggregation()}
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("app", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
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
            ${configureAggregation()}
            dependencies {
                jacocoAggregation(project(":app"))
            }
        """

        when:
        succeeds(AGGREGATION_TASK_NAME)

        then:
        def aggregatedReport = htmlReport("aggregation", AGGREGATION_TASK_NAME)
        aggregatedReport.exists()
        aggregatedReport.numberOfClasses() == 3
    }

    def "assemble does not execute tests"() {
        given:
        file("app/build.gradle") << """
            dependencies {
                implementation(project(":lib1"))
                implementation(project(":lib2"))
            }
            ${configureAggregation()}
        """

        when:
        succeeds("assemble")

        then:
        notExecuted(":app:test", ":lib1:test", ":lib2:test")
    }

    private JacocoReportFixture htmlReport(String project, String reportName) {
        return new JacocoReportFixture(file(project + "/build/reports/jacoco/${reportName}/html"))
    }

    private static String configureAggregation() {
        return """
            def implementation = configurations.findByName("implementation")
            def jacocoAggregationConfiguration = configurations.create("jacocoAggregation") {
                visible = false
                canBeResolved = false
                canBeConsumed = false
                if (implementation != null) {
                    extendsFrom(implementation)
                }
            }
            def sourcesPath = configurations.create("sourcesPath") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(jacocoAggregationConfiguration)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, "source-directories"))
                }
            }
            def coverageDataPath = configurations.create("coverageDataPath") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(jacocoAggregationConfiguration)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, "jacoco-coverage-data"))
                }
            }
            def coverageClassesDirs = configurations.create("coverageClassesDirs") {
                visible = false
                canBeResolved = true
                canBeConsumed = false
                extendsFrom(jacocoAggregationConfiguration)
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.LIBRARY));
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME));
                    attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR));
                    attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL));
                    attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment, TargetJvmEnvironment.STANDARD_JVM));
                }
            }

            tasks.register("${AGGREGATION_TASK_NAME}", JacocoReport) {
                if (project.extensions.findByType(JavaPluginExtension) != null) {
                    sourceSets(sourceSets.main)
                }
                additionalClassDirs(coverageClassesDirs.filter { it.path.contains("/build/libs/") })
                additionalSourceDirs(sourcesPath.incoming.artifactView { lenient(true) }.files)
                executionData(coverageDataPath.incoming.artifactView { lenient(true) }.files.filter { it.exists() })
                if (tasks.findByName("test") != null) {
                    executionData(tasks.named("test").map { task ->
                        task.extensions.getByType(JacocoTaskExtension).destinationFile
                    })
                }
                reports {
                    xml.required = true
                    html.required = true
                }
            }
        """
    }

    private static String registerOutgoingCoverageVariants() {
        return """
            configurations.create("transitiveSourcesElements") {
                visible = false
                canBeResolved = false
                canBeConsumed = true
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, "source-directories"))
                }
                sourceSets.main.java.srcDirs.forEach {
                    outgoing.artifact(it)
                }
            }
            configurations.create("coverageDataElements") {
                visible = false
                canBeResolved = false
                canBeConsumed = true
                extendsFrom(configurations.implementation)
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, "jacoco-coverage-data"))
                }
                outgoing.artifact(tasks.named("test").map { task ->
                    task.extensions.getByType(JacocoTaskExtension).destinationFile
                })
            }
        """
    }

    private static String configureJUnitPlatform() {
        return """
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
            }
            tasks.named("test") {
                useJUnitPlatform()
            }
        """
    }

    private void writeClassWithTest(String subproject, String className) {
        file("${subproject}/src/main/java/com/example/${className}.java") << """
            package com.example;
            public class ${className} {
                public String get${className}() {
                    return "${className}";
                }
                void untested() {
                    System.out.println();
                }
            }
        """
        file("${subproject}/src/test/java/com/example/${className}Test.java") << """
            package com.example;
            import org.junit.jupiter.api.Test;
            class ${className}Test {
                @Test
                void returns${className}() {
                    new ${className}().get${className}();
                }
            }
        """
    }
}

