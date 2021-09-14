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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.platform.base.TestSuite;
import org.gradle.platform.base.plugins.TestingExtension;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import javax.inject.Inject;


public abstract class JacocoReportAggregationPlugin implements Plugin<Project> {

    public static String JACOCO_AGGREGATION_CONFIGURATION_NAME = "jacocoAggregation";

    @Inject protected abstract JvmPluginServices getJvmPluginServices();

    @Override
    public void apply(Project project) {
        project.getPlugins().withId("jvm-test-suite", p -> {
            JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);

            Configuration jacocoAggregation = project.getConfigurations().maybeCreate(JACOCO_AGGREGATION_CONFIGURATION_NAME);

            ObjectFactory objects = project.getObjects();

            Configuration sourcesPath = project.getConfigurations().create("sourcesPath", conf -> {
                conf.setDescription("A resolvable configuration to collect source code");
                conf.setVisible(false);
                conf.setCanBeConsumed(false);
                conf.setCanBeResolved(true);

                conf.extendsFrom(jacocoAggregation);

                conf.attributes(attributes -> {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
                    attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, "source-folders"));
                });
            });

            Configuration analyzedClasses = project.getConfigurations().create("analyzedClasses", conf -> {
                conf.setDescription("A resolvable configuration to collect source code");
                conf.setVisible(false);
                conf.setCanBeConsumed(false);
                conf.setCanBeResolved(true);

                conf.extendsFrom(jacocoAggregation);
            });
            getJvmPluginServices().configureAsRuntimeClasspath(analyzedClasses);

            TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
            ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();
            testSuites.withType(JvmTestSuite.class).configureEach(testSuite -> {
                // A resolvable configuration to collect JaCoCo coverage data
                Configuration coverageDataPath = project.getConfigurations().create("coverageDataPathFor" + StringUtils.capitalize(testSuite.getName()), conf -> {
                    conf.setVisible(false);
                    conf.setCanBeConsumed(false);
                    conf.setCanBeResolved(true);

                    conf.extendsFrom(jacocoAggregation);

                    conf.attributes(attributes -> {
                        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
                        attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
                        attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, "jacoco-coverage-data"));
                        attributes.attribute(JacocoPlugin.TestSuiteType.TEST_SUITE_TYPE_ATTRIBUTE, objects.named(JacocoPlugin.TestSuiteType.class, testSuite.getName()));
                    });
                });

                // create task to do the aggregation
                TaskProvider<JacocoReport> codeCoverageReport = project.getTasks().register(testSuite.getName() + "CodeCoverageReport", JacocoReport.class, task -> {
                    // TODO add group, description
                    task.getClassDirectories().from(analyzedClasses);
                    task.getSourceDirectories().from(sourcesPath.getIncoming().artifactView(view -> view.lenient(true)).getFiles());
                    task.getExecutionData().from(coverageDataPath.getIncoming().artifactView(view -> view.lenient(true)).getFiles());

                    task.reports(reports -> {
                        // xml is usually used to integrate code coverage with
                        // other tools like SonarQube, Coveralls or Codecov
                        reports.getXml().getRequired().set(true);
                        // HTML reports can be used to see code coverage
                        // without any external tools
                        reports.getHtml().getRequired().set(true);
                    });
                });

                // TODO check task dependency
            });
        });
    }
}
