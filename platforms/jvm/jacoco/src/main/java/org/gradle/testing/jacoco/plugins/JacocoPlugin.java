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
package org.gradle.testing.jacoco.plugins;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.TestSuiteName;
import org.gradle.api.attributes.TestSuiteTargetName;
import org.gradle.api.attributes.TestSuiteType;
import org.gradle.api.attributes.VerificationType;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.reporting.SingleFileReport;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.jacoco.JacocoAgentJar;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;
import org.gradle.testing.jacoco.tasks.JacocoBase;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import javax.inject.Inject;

import static org.gradle.api.internal.lambdas.SerializableLambdas.action;

/**
 * Plugin that provides support for generating Jacoco coverage data.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/jacoco_plugin.html">JaCoCo plugin reference</a>
 */
public abstract class JacocoPlugin implements Plugin<Project> {

    /**
     * The jacoco version used if none is explicitly specified.
     *
     * @since 3.4
     */
    public static final String DEFAULT_JACOCO_VERSION = "0.8.12";
    public static final String AGENT_CONFIGURATION_NAME = "jacocoAgent";
    public static final String ANT_CONFIGURATION_NAME = "jacocoAnt";
    public static final String PLUGIN_EXTENSION_NAME = "jacoco";
    private static final String COVERAGE_DATA_ELEMENTS_VARIANT_PREFIX = "coverageDataElementsFor";

    private final Instantiator instantiator;
    private ProjectInternal project;

    @Inject
    public JacocoPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ReportingBasePlugin.class);
        this.project = (ProjectInternal) project;
        addJacocoConfigurations();
        JacocoAgentJar agent = instantiator.newInstance(JacocoAgentJar.class, this.project.getServices().get(FileOperations.class));
        JacocoPluginExtension extension = project.getExtensions().create(PLUGIN_EXTENSION_NAME, JacocoPluginExtension.class, project, agent);
        extension.getToolVersion().convention(DEFAULT_JACOCO_VERSION);
        final ReportingExtension reportingExtension = (ReportingExtension) project.getExtensions().getByName(ReportingExtension.NAME);
        extension.getReportsDirectory().convention(project.getLayout().dir(project.provider(() -> reportingExtension.file("jacoco"))));

        configureAgentDependencies(agent, extension);
        configureTaskClasspathDefaults(extension);
        applyToDefaultTasks(extension);
        configureJacocoReportsDefaults(extension);
        addDefaultReportAndCoverageVerificationTasks(extension);
        configureCoverageDataElementsVariants(project);
    }

    private void configureCoverageDataElementsVariants(Project project) {
        project.getPlugins().withType(JvmTestSuitePlugin.class, p -> {
            final TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
            final ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();

            testSuites.withType(JvmTestSuite.class).configureEach(suite -> {
                suite.getTargets().configureEach(target -> {
                    createCoverageDataVariant((ProjectInternal) project, suite, target);
                });
            });
        });
    }

    private void createCoverageDataVariant(ProjectInternal project, JvmTestSuite suite, JvmTestSuiteTarget target) {
        final Configuration variant = project.getConfigurations().migratingUnlocked(COVERAGE_DATA_ELEMENTS_VARIANT_PREFIX + StringUtils.capitalize(target.getName()), ConfigurationRolesForMigration.CONSUMABLE_DEPENDENCY_SCOPE_TO_CONSUMABLE);
        variant.setDescription("Binary data file containing results of Jacoco test coverage reporting for the " + suite.getName() + " Test Suite's " + target.getName() + " target.");
        variant.setVisible(false);

        final ObjectFactory objects = project.getObjects();
        variant.attributes(attributes -> {
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.VERIFICATION));
            attributes.attribute(TestSuiteName.TEST_SUITE_NAME_ATTRIBUTE, objects.named(TestSuiteName.class, suite.getName()));
            attributes.attribute(TestSuiteTargetName.TEST_SUITE_TARGET_NAME_ATTRIBUTE, objects.named(TestSuiteTargetName.class, target.getName()));
            attributes.attributeProvider(TestSuiteType.TEST_SUITE_TYPE_ATTRIBUTE, suite.getTestType().map(tt -> objects.named(TestSuiteType.class, tt)));
            attributes.attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, objects.named(VerificationType.class, VerificationType.JACOCO_RESULTS));
        });

        variant.getOutgoing().artifact(target.getTestTask().map(task -> task.getExtensions().getByType(JacocoTaskExtension.class).getDestinationFile()), artifact -> {
            artifact.setType(ArtifactTypeDefinition.BINARY_DATA_TYPE);
            artifact.builtBy(target.getTestTask());
        });
    }

    /**
     * Creates the configurations used by plugin.
     */
    @SuppressWarnings("deprecation")
    private void addJacocoConfigurations() {
        RoleBasedConfigurationContainerInternal configurations = project.getConfigurations();
        Configuration agentConf = configurations.resolvableDependencyScopeUnlocked(AGENT_CONFIGURATION_NAME);
        agentConf.setVisible(false);
        agentConf.setTransitive(true);
        agentConf.setDescription("The Jacoco agent to use to get coverage data.");
        Configuration antConf = configurations.resolvableDependencyScopeUnlocked(ANT_CONFIGURATION_NAME);
        antConf.setVisible(false);
        antConf.setTransitive(true);
        antConf.setDescription("The Jacoco ant tasks to use to get execute Gradle tasks.");
    }

    /**
     * Configures the agent dependencies using the 'jacocoAnt' configuration. Uses the version declared in 'toolVersion' of the Jacoco extension if no dependencies are explicitly declared.
     *
     * @param extension the extension that has the tool version to use
     */
    private void configureAgentDependencies(JacocoAgentJar jacocoAgentJar, final JacocoPluginExtension extension) {
        final Configuration config = project.getConfigurations().getAt(AGENT_CONFIGURATION_NAME);
        jacocoAgentJar.setAgentConf(config);
        config.defaultDependencies(dependencies -> dependencies.addLater(
            extension.getToolVersion().map(version -> project.getDependencies().create("org.jacoco:org.jacoco.agent:" + version))
        ));
    }

    /**
     * Configures the classpath for Jacoco tasks using the 'jacocoAnt' configuration. Uses the version information declared in 'toolVersion' of the Jacoco extension if no dependencies are explicitly
     * declared.
     *
     * @param extension the JacocoPluginExtension
     */
    private void configureTaskClasspathDefaults(final JacocoPluginExtension extension) {
        final Configuration config = this.project.getConfigurations().getAt(ANT_CONFIGURATION_NAME);
        project.getTasks().withType(JacocoBase.class).configureEach(task -> task.setJacocoClasspath(config));
        config.defaultDependencies(dependencies -> dependencies.addLater(
            extension.getToolVersion().map(version -> project.getDependencies().create("org.jacoco:org.jacoco.ant:" + version))
        ));
    }

    /**
     * Applies the Jacoco agent to all tasks of type {@code Test}.
     *
     * @param extension the extension to apply Jacoco with
     */
    private void applyToDefaultTasks(final JacocoPluginExtension extension) {
        project.getTasks().withType(Test.class).configureEach(extension::applyTo);
    }

    private void configureJacocoReportsDefaults(final JacocoPluginExtension extension) {
        project.getTasks().withType(JacocoReport.class).configureEach(reportTask -> configureJacocoReportDefaults(extension, reportTask));
    }

    private void configureJacocoReportDefaults(final JacocoPluginExtension extension, final JacocoReport reportTask) {
        reportTask.getReports().all(action(report ->
            report.getRequired().convention(report.getName().equals("html"))
        ));
        DirectoryProperty reportsDir = extension.getReportsDirectory();
        reportTask.getReports().all(action(report -> {
            if (report.getOutputType().equals(Report.OutputType.DIRECTORY)) {
                ((DirectoryReport)report).getOutputLocation().convention(reportsDir.dir(reportTask.getName() + "/" + report.getName()));
            } else {
                ((SingleFileReport)report).getOutputLocation().convention(reportsDir.file(reportTask.getName() + "/" + reportTask.getName() + "." + report.getName()));
            }
        }));
    }

    /**
     * Adds report and coverage verification tasks for specific default test tasks.
     *
     * @param extension the extension describing the test task names
     */
    private void addDefaultReportAndCoverageVerificationTasks(final JacocoPluginExtension extension) {
        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
            JvmTestSuite defaultTestSuite = testing.getSuites().withType(JvmTestSuite.class).getByName(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME);
            defaultTestSuite.getTargets().configureEach(target -> {
                TaskProvider<Test> testTask = target.getTestTask();
                addDefaultReportTask(extension, testTask);
                addDefaultCoverageVerificationTask(testTask);
            });
        });
    }

    private void addDefaultReportTask(final JacocoPluginExtension extension, final TaskProvider<? extends Task> testTaskProvider) {
        final String testTaskName = testTaskProvider.getName();
        project.getTasks().register(
            "jacoco" + StringUtils.capitalize(testTaskName) + "Report",
            JacocoReport.class,
            reportTask -> {
                reportTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                reportTask.setDescription(String.format("Generates code coverage report for the %s task.", testTaskName));
                reportTask.executionData(testTaskProvider.get());
                reportTask.sourceSets(project.getExtensions().getByType(SourceSetContainer.class).getByName("main"));
                // TODO: Change the default location for these reports to follow the convention defined in ReportOutputDirectoryAction
                DirectoryProperty reportsDir = extension.getReportsDirectory();
                reportTask.getReports().all(action(report -> {
                    // For someone looking for the difference between this and the duplicate code above
                    // this one uses the `testTaskProvider` and the `reportTask`. The other just
                    // uses the `reportTask`.
                    // https://github.com/gradle/gradle/issues/6343
                    if (report.getOutputType().equals(Report.OutputType.DIRECTORY)) {
                        ((DirectoryReport)report).getOutputLocation().convention(reportsDir.dir(testTaskName + "/" + report.getName()));
                    } else {
                        ((SingleFileReport)report).getOutputLocation().convention(reportsDir.file(testTaskName + "/" + reportTask.getName() + "." + report.getName()));
                    }
                }));
            });
    }

    private void addDefaultCoverageVerificationTask(final TaskProvider<? extends Task> testTaskProvider) {
        project.getTasks().register(
            "jacoco" + StringUtils.capitalize(testTaskProvider.getName()) + "CoverageVerification",
            JacocoCoverageVerification.class,
            coverageVerificationTask -> {
                coverageVerificationTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                coverageVerificationTask.setDescription(String.format("Verifies code coverage metrics based on specified rules for the %s task.", testTaskProvider.getName()));
                coverageVerificationTask.executionData(testTaskProvider.get());
                coverageVerificationTask.sourceSets(project.getExtensions().getByType(SourceSetContainer.class).getByName("main"));
            });
    }
}
