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

package org.gradle.testing.jacoco.plugins;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.TestSuiteType;
import org.gradle.api.attributes.VerificationType;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.internal.jacoco.DefaultJacocoCoverageReport;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Adds configurations to for resolving variants containing JaCoCo code coverage results, which may span multiple subprojects.  Reacts to the presence of the jvm-test-suite plugin and creates
 * tasks to collect code coverage results for each named test-suite.
 *
 * @since 7.4
 * @see <a href="https://docs.gradle.org/current/userguide/jacoco_report_aggregation_plugin.html">JaCoCo Report Aggregation Plugin reference</a>
 */
@Incubating
public abstract class JacocoReportAggregationPlugin implements Plugin<Project> {
    private static final Set<Integer> ZIP_SIGNATURES = ImmutableSet.of(0x504B0304, 0x504B0506, 0x504B0708);
    public static final String JACOCO_AGGREGATION_CONFIGURATION_NAME = "jacocoAggregation";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.gradle.reporting-base");
        project.getPluginManager().apply("jvm-ecosystem");
        project.getPluginManager().apply("jacoco");

        Configuration jacocoAggregation = project.getConfigurations().create(JACOCO_AGGREGATION_CONFIGURATION_NAME);
        jacocoAggregation.setDescription("Collects project dependencies for purposes of JaCoCo coverage report aggregation");
        jacocoAggregation.setVisible(false);
        jacocoAggregation.setCanBeConsumed(false);
        jacocoAggregation.setCanBeResolved(false);

        ObjectFactory objects = project.getObjects();
        Configuration sourceDirectoriesConf = project.getConfigurations().create("allCodeCoverageReportSourceDirectories");
        sourceDirectoriesConf.setDescription("Supplies the source directories used to produce all aggregated JaCoCo coverage data reports");
        sourceDirectoriesConf.extendsFrom(jacocoAggregation);
        sourceDirectoriesConf.setVisible(false);
        sourceDirectoriesConf.setCanBeConsumed(false);
        sourceDirectoriesConf.setCanBeResolved(true);
        sourceDirectoriesConf.attributes(attributes -> {
            attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.class, Bundling.EXTERNAL));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.VERIFICATION));
            attributes.attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.MAIN_SOURCES));
        });

        ArtifactView sourceDirectories = sourceDirectoriesConf.getIncoming().artifactView(view -> {
            view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
            view.lenient(true);
        });

        Configuration classDirectoriesConf = project.getConfigurations().create("allCodeCoverageReportClassDirectories");
        classDirectoriesConf.extendsFrom(jacocoAggregation);
        classDirectoriesConf.setDescription("Supplies the class directories used to produce all aggregated JaCoCo coverage data reports");
        classDirectoriesConf.setVisible(false);
        classDirectoriesConf.setCanBeConsumed(false);
        classDirectoriesConf.setCanBeResolved(true);

        ArtifactView classDirectories = classDirectoriesConf.getIncoming().artifactView(view -> {
            view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
        });

        ReportingExtension reporting = project.getExtensions().getByType(ReportingExtension.class);
        reporting.getReports().registerBinding(JacocoCoverageReport.class, DefaultJacocoCoverageReport.class);

        // iterate and configure each user-specified report, creating a <reportName>ExecutionData configuration for each
        reporting.getReports().withType(JacocoCoverageReport.class).all(report -> {
            // A resolvable configuration to collect JaCoCo coverage data; typically named "testCodeCoverageReportExecutionData"
            Configuration executionDataConf = project.getConfigurations().create(report.getName() + "ExecutionData");
            executionDataConf.extendsFrom(jacocoAggregation);
            executionDataConf.setDescription(String.format("Supplies JaCoCo coverage data to the %s.  External library dependencies may appear as resolution failures, but this is expected behavior.", report.getName()));
            executionDataConf.setVisible(false);
            executionDataConf.setCanBeConsumed(false);
            executionDataConf.setCanBeResolved(true);
            executionDataConf.attributes(attributes -> {
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.VERIFICATION));
                attributes.attributeProvider(TestSuiteType.TEST_SUITE_TYPE_ATTRIBUTE, report.getTestType().map(tt -> objects.named(TestSuiteType.class, tt)));
                attributes.attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.JACOCO_RESULTS));
            });

            report.getReportTask().configure(task -> {
                Callable<FileCollection> executionData = () ->
                    executionDataConf.getIncoming().artifactView(view -> {
                        view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
                        view.lenient(true);
                    }).getFiles().filter(f -> !isArchive(f)); // Filter out archive files, to work around #20532

                configureReportTaskInputs(task, classDirectories, sourceDirectories, executionData);
            });
        });

        // convention for synthesizing reports based on existing test suites in "this" project
        project.getPlugins().withId("jvm-test-suite", plugin -> {
            // Depend on this project for aggregation
            project.getDependencies().add(JACOCO_AGGREGATION_CONFIGURATION_NAME, project);

            TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
            ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();
            testSuites.withType(JvmTestSuite.class).all(testSuite -> {
                reporting.getReports().create(testSuite.getName() + "CodeCoverageReport", JacocoCoverageReport.class, report -> {
                    report.getTestType().convention(testSuite.getTestType());
                });
            });
        });
    }

    private void configureReportTaskInputs(JacocoReport task, ArtifactView classDirectories, ArtifactView sourceDirectories, Callable<FileCollection> executionData) {
        task.getExecutionData().from(executionData);
        task.getClassDirectories().from(classDirectories.getFiles());
        task.getSourceDirectories().from(sourceDirectories.getFiles());
    }

    /**
     * Read the first few magic bytes of the file and compare them to known zip signatures to determine if it is an archive.
     *
     * @see <a href="https://en.wikipedia.org/wiki/List_of_file_signatures">List of file signatures</a>
     * @param f the file to check
     * @return {@code true} if the file is an archive, {@code false} otherwise
     */
    private static boolean isArchive(File f) {
        if (f.exists()) {
            try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                return ZIP_SIGNATURES.contains(raf.readInt());
            } catch (IOException e) {
                throw new RuntimeException("Error checking file signature", e);
            }
        } else {
            return false;
        }
    }
}
