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

import com.google.common.util.concurrent.Callables;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.reporting.Report;
import org.gradle.api.reporting.ReportingExtension;
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
@Incubating
public class JacocoPlugin implements Plugin<ProjectInternal> {

    /**
     * The jacoco version used if none is explicitly specified.
     * @since 3.4
     */
    public static final String DEFAULT_JACOCO_VERSION = "0.7.8";
    public static final String AGENT_CONFIGURATION_NAME = "jacocoAgent";
    public static final String ANT_CONFIGURATION_NAME = "jacocoAnt";
    public static final String PLUGIN_EXTENSION_NAME = "jacoco";
    private final Instantiator instantiator;
    private Project project;

    @Inject
    public JacocoPlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void apply(ProjectInternal project) {
        project.getPluginManager().apply(ReportingBasePlugin.class);
        this.project = project;
        addJacocoConfigurations();
        JacocoAgentJar agent = instantiator.newInstance(JacocoAgentJar.class, project);
        JacocoPluginExtension extension = project.getExtensions().create(PLUGIN_EXTENSION_NAME, JacocoPluginExtension.class, project, agent);
        extension.setToolVersion(DEFAULT_JACOCO_VERSION);
        final ReportingExtension reportingExtension = (ReportingExtension) project.getExtensions().getByName(ReportingExtension.NAME);
        ((IConventionAware) extension).getConventionMapping().map("reportsDir", new Callable<File>() {
            @Override
            public File call() {
                return reportingExtension.file("jacoco");
            }
        });

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
        Configuration config = project.getConfigurations().getAt(AGENT_CONFIGURATION_NAME);
        ((IConventionAware) jacocoAgentJar).getConventionMapping().map("agentConf", Callables.returning(config));
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
        project.getTasks().withType(JacocoBase.class, new Action<JacocoBase>() {
            @Override
            public void execute(JacocoBase task) {
                ((IConventionAware) task).getConventionMapping().map("jacocoClasspath", Callables.returning(config));
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
        project.getTasks().withType(Test.class, new Action<Test>() {
            @Override
            public void execute(Test task) {
                extension.applyTo(task);
            }
        });
    }

    public Object configureDefaultOutputPathForJacocoMerge() {
        return project.getTasks().withType(JacocoMerge.class, new Action<JacocoMerge>() {
            @Override
            public void execute(final JacocoMerge task) {
                ConventionMapping mapping = ((IConventionAware) task).getConventionMapping();
                mapping.map("destinationFile", new Callable<File>() {
                    @Override
                    public File call() {
                        return new File(project.getBuildDir(), "/jacoco/" + task.getName() + ".exec");
                    }
                });
            }
        });
    }


    private void configureJacocoReportsDefaults(final JacocoPluginExtension extension) {
        project.getTasks().withType(JacocoReport.class, new Action<JacocoReport>() {
            @Override
            public void execute(JacocoReport reportTask) {
                configureJacocoReportDefaults(extension, reportTask);
            }
        });
    }

    private void configureJacocoReportDefaults(final JacocoPluginExtension extension, final JacocoReport reportTask) {
        reportTask.getReports().all(new Action<Report>() {
            @Override
            public void execute(final Report report) {
                ConventionMapping mapping = ((IConventionAware) report).getConventionMapping();
                mapping.map("enabled", Callables.returning(report.getName().equals("html")));
                if (report.getOutputType().equals(Report.OutputType.DIRECTORY)) {
                    mapping.map("destination", new Callable<File>() {
                        @Override
                        public File call() {
                            return new File(extension.getReportsDir(), reportTask.getName() + "/" + report.getName());
                        }
                    });
                } else {
                    mapping.map("destination", new Callable<File>() {
                        @Override
                        public File call() {
                            return new File(extension.getReportsDir(), reportTask.getName() + "/" + reportTask.getName() + "." + report.getName());
                        }
                    });
                }
            }
        });
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
                project.getTasks().withType(Test.class, new Action<Test>() {
                    @Override
                    public void execute(Test task) {
                        if (task.getName().equals(JavaPlugin.TEST_TASK_NAME)) {
                            addDefaultReportTask(extension, task);
                            addDefaultCoverageVerificationTask(task);
                        }
                    }
                });
            }
        });
    }

    private void addDefaultReportTask(final JacocoPluginExtension extension, final Test task) {
        final JacocoReport reportTask = project.getTasks().create("jacoco" + StringUtils.capitalize(task.getName()) + "Report", JacocoReport.class);
        reportTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
        reportTask.setDescription(String.format("Generates code coverage report for the %s task.", task.getName()));
        reportTask.executionData(task);
        reportTask.sourceSets(project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main"));
        ConventionMapping taskMapping = ((IConventionAware) reportTask).getConventionMapping();
        taskMapping.getConventionValue(reportTask.getReports(), "reports", false).all(new Action<Report>() {
            @Override
            public void execute(final Report report) {
                ConventionMapping reportMapping = ((IConventionAware) report).getConventionMapping();
                // reportMapping.map('enabled', Callables.returning(true));
                if (report.getOutputType().equals(Report.OutputType.DIRECTORY)) {
                    reportMapping.map("destination", new Callable<File>() {
                        @Override
                        public File call() {
                            return new File(extension.getReportsDir(), task.getName() + "/" + report.getName());
                        }
                    });
                } else {
                    reportMapping.map("destination", new Callable<File>() {
                        @Override
                        public File call() {
                            return new File(extension.getReportsDir(), task.getName() + "/" + reportTask.getName() + "." + report.getName());
                        }
                    });
                }
            }
        });
    }

    private void addDefaultCoverageVerificationTask(final Test task) {
        final JacocoCoverageVerification coverageVerificationTask = project.getTasks().create("jacoco" + StringUtils.capitalize(task.getName()) + "CoverageVerification", JacocoCoverageVerification.class);
        coverageVerificationTask.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
        coverageVerificationTask.setDescription(String.format("Verifies code coverage metrics based on specified rules for the %s task.", task.getName()));
        coverageVerificationTask.executionData(task);
        coverageVerificationTask.sourceSets(project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main"));
    }
}
