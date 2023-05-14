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

package org.gradle.api.plugins;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.TestSuiteType;
import org.gradle.api.attributes.VerificationType;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.testing.DefaultAggregateTestReport;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.JvmEcosystemUtilities;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.testing.AggregateTestReport;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;
import org.gradle.testing.base.plugins.TestingBasePlugin;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * Adds configurations to for resolving variants containing test execution results, which may span multiple subprojects.  Reacts to the presence of the jvm-test-suite plugin and creates
 * tasks to collect test results for each named test-suite.
 *
 * @since 7.4
 * @see <a href="https://docs.gradle.org/current/userguide/test_report_aggregation_plugin.html">Test Report Aggregation Plugin reference</a>
 */
@Incubating
public abstract class TestReportAggregationPlugin implements Plugin<Project> {

    public static final String TEST_REPORT_AGGREGATION_CONFIGURATION_NAME = "testReportAggregation";

    @Inject
    protected abstract JvmEcosystemUtilities getEcosystemUtilities();

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.gradle.reporting-base");

        RoleBasedConfigurationContainerInternal configurations = ((ProjectInternal) project).getConfigurations();
        final Configuration testAggregation = configurations.bucket(TEST_REPORT_AGGREGATION_CONFIGURATION_NAME);
        testAggregation.setDescription("A configuration to collect test execution results");
        testAggregation.setVisible(false);

        ReportingExtension reporting = project.getExtensions().getByType(ReportingExtension.class);
        reporting.getReports().registerBinding(AggregateTestReport.class, DefaultAggregateTestReport.class);

        ObjectFactory objects = project.getObjects();

        final DirectoryProperty testReportDirectory = objects.directoryProperty().convention(reporting.getBaseDirectory().dir(TestingBasePlugin.TESTS_DIR_NAME));
        // prepare testReportDirectory with a reasonable default, but override with JavaPluginExtension#testReportDirectory if available
        project.getPlugins().withId("java-base", plugin -> {
            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            testReportDirectory.convention(javaPluginExtension.getTestReportDir());
        });

        // A resolvable configuration to collect test results
        Configuration testResultsConf = configurations.resolvable("aggregateTestReportResults");
        testResultsConf.extendsFrom(testAggregation);
        testResultsConf.setDescription("Graph needed for the aggregated test results report.");
        testResultsConf.setVisible(false);

        // Iterate and configure each user-specified report.
        reporting.getReports().withType(AggregateTestReport.class).all(report -> {
            report.getReportTask().configure(task -> {
                Callable<FileCollection> testResults = () ->
                    testResultsConf.getIncoming().artifactView(view -> {
                        view.withVariantReselection();
                        view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
                        view.attributes(attributes -> {
                            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.VERIFICATION));
                            attributes.attributeProvider(TestSuiteType.TEST_SUITE_TYPE_ATTRIBUTE, report.getTestType().map(tt -> objects.named(TestSuiteType.class, tt)));
                            attributes.attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.TEST_RESULTS));
                        });
                    }).getFiles();

                task.getTestResults().from(testResults);
                task.getDestinationDirectory().convention(testReportDirectory.dir(report.getTestType().map(tt -> tt + "/aggregated-results")));
            });
        });

        project.getPlugins().withType(JavaBasePlugin.class, plugin -> {
            // If the current project is jvm-based, aggregate dependent projects as jvm-based as well.
            getEcosystemUtilities().configureAsRuntimeClasspath(testAggregation);
        });

        // convention for synthesizing reports based on existing test suites in "this" project
        project.getPlugins().withId("jvm-test-suite", plugin -> {
            // Depend on this project for aggregation
            testAggregation.getDependencies().add(project.getDependencyFactory().create(project));

            TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
            ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();

            testSuites.withType(JvmTestSuite.class).all(testSuite -> {
                reporting.getReports().create(testSuite.getName() + "AggregateTestReport", AggregateTestReport.class, report -> {
                    report.getTestType().convention(testSuite.getTestType());
                });
            });
        });
    }
}
