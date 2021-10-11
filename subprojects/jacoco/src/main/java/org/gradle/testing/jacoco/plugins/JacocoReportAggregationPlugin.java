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

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.internal.jacoco.DefaultJacocoCoverageReport;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;

import javax.inject.Inject;


public abstract class JacocoReportAggregationPlugin implements Plugin<Project> {

    public static String JACOCO_AGGREGATION_CONFIGURATION_NAME = "jacocoAggregation";

    @Inject protected abstract JvmPluginServices getJvmPluginServices();

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.gradle.reporting-base");
        project.getPluginManager().apply("jacoco");

        Configuration jacocoAggregation = project.getConfigurations().create(JACOCO_AGGREGATION_CONFIGURATION_NAME, conf -> {
            conf.setDescription("A resolvable configuration to collect source code");
            conf.setVisible(false);
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
        });
        getJvmPluginServices().configureAsRuntimeClasspath(jacocoAggregation);

        ObjectFactory objects = project.getObjects();
        ArtifactView sourcesPath = jacocoAggregation.getIncoming().artifactView(view -> {
            view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
            view.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, "source-folders"));
            });
        });
        ArtifactView analyzedClasses = jacocoAggregation.getIncoming().artifactView(view -> {
            view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
        });

        ReportingExtension reporting = project.getExtensions().getByType(ReportingExtension.class);
        reporting.getReports().registerBinding(JacocoCoverageReport.class, DefaultJacocoCoverageReport.class);

        // TODO check task dependency

        reporting.getReports().withType(JacocoCoverageReport.class).configureEach(report -> {
            report.getClasses().from(analyzedClasses.getFiles());
            report.getSources().from(sourcesPath.getFiles());
        });
        project.getPlugins().withId("jvm-test-suite", p -> {
            // Depend on this project for aggregation
            project.getDependencies().add(JACOCO_AGGREGATION_CONFIGURATION_NAME, project);

            TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
            ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();
            testSuites.withType(JvmTestSuite.class).configureEach(testSuite -> {
                reporting.getReports().create(testSuite.getName() + "CodeCoverageReport", JacocoCoverageReport.class, report -> {
                    report.getExecutionData().from(resolvableJacocoData(jacocoAggregation, objects, testSuite.getName()));
                });
//                testSuite.getTargets().all(target -> {
//                    target.getTestTask().configure(test -> {
//                        test.
//                    });
//                });
            });
        });
    }

    public static FileCollection resolvableJacocoData(Configuration jacocoAggregation, ObjectFactory objects, String name) {
        // A resolvable configuration to collect JaCoCo coverage data
        ArtifactView coverageDataPath = jacocoAggregation.getIncoming().artifactView(view -> {
            view.componentFilter(id -> id instanceof ProjectComponentIdentifier);
            view.lenient(true);
            view.attributes(attributes -> {
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
                attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
                attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, "jacoco-coverage-data"));
                // TODO: need to support provider
                attributes.attribute(JacocoPlugin.TestSuiteType.TEST_SUITE_TYPE_ATTRIBUTE, objects.named(JacocoPlugin.TestSuiteType.class, name));
            });
        });
        return coverageDataPath.getFiles();
    }
}
