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
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.jacoco.JacocoAgentJar;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.jacoco.tasks.JacocoBase;
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification;
import org.gradle.testing.jacoco.tasks.JacocoMerge;
import org.gradle.testing.jacoco.tasks.JacocoReport;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Plugin that provides support for generating Jacoco coverage data.
 */
public class JacocoPlugin implements Plugin<Project> {

    /**
     * The jacoco version used if none is explicitly specified.
     * @since 3.4
     */
    public static final String DEFAULT_JACOCO_VERSION = "0.8.5";
    public static final String AGENT_CONFIGURATION_NAME = "jacocoAgent";
    public static final String ANT_CONFIGURATION_NAME = "jacocoAnt";
    public static final String PLUGIN_EXTENSION_NAME = "jacoco";
    private final Instantiator instantiator;
    private Project project;

    @Inject
    public JacocoPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ReportingBasePlugin.class);
        this.project = project;
        addJacocoConfigurations();
        ProjectInternal projectInternal = (ProjectInternal) project;
        JacocoAgentJar agent = instantiator.newInstance(JacocoAgentJar.class, projectInternal.getServices().get(FileOperations.class));
        JacocoPluginExtension extension = project.getExtensions().create(PLUGIN_EXTENSION_NAME, JacocoPluginExtension.class, project, agent);
        extension.setToolVersion(DEFAULT_JACOCO_VERSION);
        final ReportingExtension reportingExtension = (ReportingExtension) project.getExtensions().getByName(ReportingExtension.NAME);
        extension.setReportsDir(project.provider(new Callable<File>() {
            @Override
            public File call() throws Exception {
                return reportingExtension.file("jacoco");
            }
        }));

        configureAgentDependencies(agent, extension);
        configureTaskClasspathDefaults(extension);
        applyToDefaultTasks(extension);
        configureDefaultOutputPathForJacocoMerge();
        configureJacocoReportsDefaults(extension);
        addDefaultReportAndCoverageVerificationTasks(extension);
    }

    /**
     * Creates the configurations used by plugin.
     */
    private void addJacocoConfigurations() {
        Configuration agentConf = project.getConfigurations().create(AGENT_CONFIGURATION_NAME);
        agentConf.setVisible(false);
        agentConf.setTransitive(true);
        agentConf.setDescription("The Jacoco agent to use to get coverage data.");
        Configuration antConf = project.getConfigurations().create(ANT_CONFIGURATION_NAME);
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
        config.defaultDependencies(new Action<DependencySet>() {
            @Override
            public void execute(DependencySet dependencies) {
                dependencies.add(project.getDependencies().create("org.jacoco:org.jacoco.agent:" + extension.getToolVersion()));
            }
        });
    }

    /**
     * Configures the classpath for Jacoco tasks using the 'jacocoAnt' configuration. Uses the version information declared in 'toolVersion' of the Jacoco extension if no dependencies are explicitly
     * declared.
     *
     * @param extension the JacocoPluginExtension
     */
    private void configureTaskClasspathDefaults(final JacocoPluginExtension extension) {
        final Configuration config = this.project.getConfigurations().getAt(ANT_CONFIGURATION_NAME);
        project.getTasks().withType(JacocoBase.class).configureEach(new Action<JacocoBase>() {
            @Override
            public void execute(JacocoBase task) {
                task.setJacocoClasspath(config);
            }
        });
        config.defaultDependencies(new Action<DependencySet>() {
            @Override
            public void execute(DependencySet dependencies) {
                dependencies.add(project.getDependencies().create("org.jacoco:org.jacoco.ant:" + extension.getToolVersion()));
            }
        });
    }

    /**
     * Applies the Jacoco agent to all tasks of type {@code Test}.
     *
     * @param extension the extension to apply Jacoco with
     */
    private void applyToDefaultTasks(final JacocoPluginExtension extension) {
        project.getTasks().withType(Test.class).configureEach(new Action<Test>() {
            @Override
            public void execute(Test task) {
                extension.applyTo(task);
            }
        });
    }

    private void configureDefaultOutputPathForJacocoMerge() {
        project.getTasks().withType(JacocoMerge.class).configureEach(new Action<JacocoMerge>() {
            @Override
            public void execute(final JacocoMerge task) {
                task.setDestinationFile(project.provider(new Callable<File>() {
                    @Override
                    public File call() {
                        return new File(project.getBuildDir(), "/jacoco/" + task.getName() + ".exec");
                    }
                }));
            }
        });
    }

    private void configureJacocoReportsDefaults(final JacocoPluginExtension extension) {
        project.getTasks().withType(JacocoReport.class).configureEach(new Action<JacocoReport>() {
            @Override
            public void execute(JacocoReport reportTask) {
                configureJacocoReportDefaults(extension, reportTask);
            }
        });
    }

    private Action<ConfigurableReport> configureReportOutputDirectory(final JacocoPluginExtension extension, final JacocoReport reportTask) {
        return new Action<ConfigurableReport>() {
            @Override
            public void execute(final ConfigurableReport report) {
                if (report.getOutputType().equals(Report.OutputType.DIRECTORY)) {
                    report.setDestination(project.provider(new Callable<File>() {
                        @Override
                        public File call() throws Exception {
                            return new File(extension.getReportsDir(), reportTask.getName() + "/" + report.getName());
                        }
                    }));
                } else {
                    report.setDestination(project.provider(new Callable<File>() {
                        @Override
                        public File call() throws Exception {
                            return new File(extension.getReportsDir(), reportTask.getName() + "/" + reportTask.getName() + "." + report.getName());
                        }
                    }));
                }
            }
        };
    }

    private void configureJacocoReportDefaults(final JacocoPluginExtension extension, final JacocoReport reportTask) {
        reportTask.getReports().all(new Action<ConfigurableReport>() {
            @Override
            public void execute(final ConfigurableReport report) {
                report.setEnabled(project.provider(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return report.getName().equals("html");
                    }
                }));
            }
        });
        reportTask.getReports().all(configureReportOutputDirectory(extension, reportTask));
    }

    /**
     * Adds report and coverage verification tasks for specific default test tasks.
     *
     * @param extension the extension describing the test task names
     */
    private void addDefaultReportAndCoverageVerificationTasks(final JacocoPluginExtension extension) {
        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            @Override
            public void execute(JavaPlugin javaPlugin) {
                final TaskProvider<Task> testTaskProvider = project.getTasks().named(JavaPlugin.TEST_TASK_NAME);
                addDefaultReportTask(extension, testTaskProvider);
                addDefaultCoverageVerificationTask(testTaskProvider);
            }
        });
    }

    private void addDefaultReportTask(final JacocoPluginExtension extension, final TaskProvider<Task> testTaskProvider) {
        project.getTasks().register(
            "jacoco" + StringUtils.capitalize(testTaskProvider.getName()) + "Report",
            JacocoReport.class,
            new Action<JacocoReport>() {
                @Override
                public void execute(final JacocoReport reportTask) {
                    reportTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    reportTask.setDescription(String.format("Generates code coverage report for the %s task.", testTaskProvider.getName()));
                    reportTask.executionData(testTaskProvider.get());
                    reportTask.sourceSets(project.getExtensions().getByType(SourceSetContainer.class).getByName("main"));
                    // TODO: Change the default location for these reports to follow the convention defined in #configureReportOutputDirectory
                    reportTask.getReports().all(new Action<ConfigurableReport>() {
                        @Override
                        public void execute(final ConfigurableReport report) {
                            /*
                             * For someone looking for the difference between this and the duplicate code above
                             * this one uses the `testTaskProvider` and the `reportTask`. The other just
                             * uses the `reportTask`.
                             * https://github.com/gradle/gradle/issues/6343
                             */
                            if (report.getOutputType().equals(Report.OutputType.DIRECTORY)) {
                                report.setDestination(project.provider(new Callable<File>() {
                                    @Override
                                    public File call() throws Exception {
                                        return new File(extension.getReportsDir(), testTaskProvider.getName() + "/" + report.getName());
                                    }
                                }));
                            } else {
                                report.setDestination(project.provider(new Callable<File>() {
                                    @Override
                                    public File call() throws Exception {
                                        return new File(extension.getReportsDir(), testTaskProvider.getName() + "/" + reportTask.getName() + "." + report.getName());
                                    }
                                }));
                            }
                        }
                    });
                }
            });
    }

    private void addDefaultCoverageVerificationTask(final TaskProvider<Task> testTaskProvider) {
        project.getTasks().register(
            "jacoco" + StringUtils.capitalize(testTaskProvider.getName()) + "CoverageVerification",
            JacocoCoverageVerification.class,
            new Action<JacocoCoverageVerification>() {
                @Override
                public void execute(final JacocoCoverageVerification coverageVerificationTask) {
                    coverageVerificationTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    coverageVerificationTask.setDescription(String.format("Verifies code coverage metrics based on specified rules for the %s task.", testTaskProvider.getName()));
                    coverageVerificationTask.executionData(testTaskProvider.get());
                    coverageVerificationTask.sourceSets(project.getExtensions().getByType(SourceSetContainer.class).getByName("main"));
                }
            });
    }
}
