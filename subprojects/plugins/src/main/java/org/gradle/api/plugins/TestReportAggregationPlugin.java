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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.TestSuiteType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.testing.DefaultAggregateTestReport;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.testing.AggregateTestReport;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;

import javax.inject.Inject;

public abstract class TestReportAggregationPlugin implements Plugin<Project> {

    public static String TEST_REPORT_AGGREGATION_CONFIGURATION_NAME = "testReportAggregation";

    @Inject
    protected abstract JvmPluginServices getJvmPluginServices();

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.gradle.reporting-base");

        final Configuration testAggregation = project.getConfigurations().create(TEST_REPORT_AGGREGATION_CONFIGURATION_NAME, conf -> {
            conf.setDescription("A resolvable configuration to collect test execution results");
            conf.setVisible(false);
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
        });
        getJvmPluginServices().configureAsRuntimeClasspath(testAggregation);

        ReportingExtension reporting = project.getExtensions().getByType(ReportingExtension.class);
        reporting.getReports().registerBinding(AggregateTestReport.class, DefaultAggregateTestReport.class); // todo this creates the task

//        reporting.getReports().withType(AggregateTestReport.class).configureEach(report -> {
//            report.getBinaryResults().from(testAggregation.)
//        });

        //TaskProvider<TestReport> aggregationTask = project.getTasks().register("it", TestReport.class);

        // TODO something.foreach aggregationTask.
//        aggregationTask.configure(new Action<TestReport>() {
//            @Override
//            public void execute(TestReport task) {
//                task.reportOn(it); // test.getBinaryResultsDirectory()
//            }
//        });

        ObjectFactory objects = project.getObjects();
        JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
        project.getPlugins().withId("jvm-test-suite", p -> {
            // Depend on this project for aggregation
            project.getDependencies().add(TEST_REPORT_AGGREGATION_CONFIGURATION_NAME, project);

            TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
            ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();
            testSuites.withType(JvmTestSuite.class).configureEach(testSuite -> {
                reporting.getReports().create(testSuite.getName() + "AggregateTestReport", AggregateTestReport.class, report -> {
                    report.getBinaryResults().from(resolvableTestResultData(testAggregation, objects, testSuite.getName()));
                    report.getDestinationDir().convention(javaPluginExtension.getTestResultsDir().dir(testSuite.getName() + "/aggregated-results"));
//                    testSuite.getTargets().all(target -> {
//                        report.getTestTasks().add(target.getTestTask().get());
//                        report.getReportTask().configure(reportTask -> reportTask.dependsOn(target.getTestTask()));
//                    });
                });
//                testSuite.getTargets().all(target -> {
//                    target.getTestTask().configure(test -> {
//                        test.
//                    });
//                });
            });
        });
    }

    public static FileCollection resolvableTestResultData(Configuration testAggregation, ObjectFactory objects, String name) {
        // A resolvable configuration to collect test result data
        ArtifactView resultsDataPath = testAggregation.getIncoming().artifactView(view -> {
            view.componentFilter(it -> it instanceof ProjectComponentIdentifier);
            //view.lenient(true);
            view.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, "test-result-data"));
                // TODO: need to support provider
                attributes.attribute(TestSuiteType.TEST_SUITE_TYPE_ATTRIBUTE, objects.named(TestSuiteType.class, name));
            });
        });
        return resultsDataPath.getFiles();
    }
}
