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
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.TestSuiteType;
import org.gradle.api.attributes.VerificationType;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.testing.AggregateTestReport;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;
import org.gradle.testing.base.plugins.TestSuiteBasePlugin;
import org.gradle.testing.base.plugins.TestingBasePlugin;

import javax.inject.Inject;

import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

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
    protected abstract JvmPluginServices getJvmPluginServices();

    @Override
    public void apply(Project project) {

        ConfigurationContainer configurations = project.getConfigurations();
        final Configuration testAggregation = configurations.dependencyScope(TEST_REPORT_AGGREGATION_CONFIGURATION_NAME, conf -> {
            conf.setDescription("A configuration to collect test execution results");
        }).get();

        project.getPluginManager().apply(TestSuiteBasePlugin.class);
        ReportingExtension reporting = project.getExtensions().getByType(ReportingExtension.class);

        ObjectFactory objects = project.getObjects();

        final DirectoryProperty testReportDirectory = objects.directoryProperty().convention(reporting.getBaseDirectory().dir(TestingBasePlugin.TESTS_DIR_NAME));
        // prepare testReportDirectory with a reasonable default, but override with JavaPluginExtension#testReportDirectory if available
        project.getPlugins().withId("java-base", plugin -> {
            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            testReportDirectory.convention(javaPluginExtension.getTestReportDir());
        });

        // A resolvable configuration to collect test results
        Configuration testResultsConf = configurations.resolvable("aggregateTestReportResults", conf -> {
            conf.extendsFrom(testAggregation);
            conf.setDescription("Graph needed for the aggregated test results report.");
        }).get();

        // Iterate and configure each user-specified report.
        reporting.getReports().withType(AggregateTestReport.class).configureEach(report -> {
            Provider<FileCollection> testResults = report.getTestType().map(tt ->
                testResultsConf.getIncoming().artifactView(view -> {
                    view.withVariantReselection();
                    view.componentFilter(spec(id -> id instanceof ProjectComponentIdentifier));
                    view.attributes(attributes -> {
                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.VERIFICATION));
                        attributes.attribute(TestSuiteType.TEST_SUITE_TYPE_ATTRIBUTE, objects.named(TestSuiteType.class, tt));
                        attributes.attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.TEST_RESULTS));
                    });
                }).getFiles()
            );

            Provider<Directory> reportDir = testReportDirectory.dir(report.getTestType().map(tt -> tt + "/aggregated-results"));
            report.getHtmlReportDirectory().convention(reportDir);
            report.getBinaryTestResults().from(testResults.orElse(FileCollectionFactory.empty()));
        });

        project.getPlugins().withType(JavaBasePlugin.class, plugin -> {
            // If the current project is jvm-based, aggregate dependent projects as jvm-based as well.
            getJvmPluginServices().configureAsRuntimeClasspath(testResultsConf);

            // Aggregate any test results from the production code and its dependencies
            // TODO: Projects applying java-base may not have production code.
            // We should only do this if the java plugin was applied
            testAggregation.getDependencies().add(project.getDependencyFactory().create(project));
        });

        // convention for synthesizing reports based on existing test suites in "this" project
        project.getPlugins().withId("jvm-test-suite", plugin -> {
            TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
            ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();

            testSuites.withType(JvmTestSuite.class).all(testSuite ->
                reporting.getReports().register(testSuite.getName() + "AggregateTestReport", AggregateTestReport.class, report -> {
                    report.getTestType().convention(testSuite.getTestType());
                })
            );
        });
    }
}
