/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationFinder;
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Displays the dependency tree for a configuration.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class AbstractDependencyReportTask extends AbstractProjectBasedReportTask<AbstractDependencyReportTask.DependencyReportModel> {

    private DependencyReportRenderer renderer = new AsciiDependencyReportRenderer();

    private transient Set<Configuration> configurations;

    @Override
    public ReportRenderer getRenderer() {
        return renderer;
    }

    /**
     * Set the renderer to use to build a report. If unset, AsciiGraphRenderer will be used.
     */
    public void setRenderer(DependencyReportRenderer renderer) {
        this.renderer = renderer;
    }


    /**
     * Report model.
     *
     * @since 7.6
     */
    @Incubating
    public static final class DependencyReportModel {
        private final List<ConfigurationDetails> configurations;

        private DependencyReportModel(List<ConfigurationDetails> configurations) {
            this.configurations = configurations;
        }
    }

    @Override
    protected DependencyReportModel calculateReportModelFor(Project project) {
        SortedSet<Configuration> sortedConfigurations = new TreeSet<>(Comparator.comparing(Configuration::getName));
        sortedConfigurations.addAll(getReportConfigurations());
        List<ConfigurationDetails> configurationDetails = new ArrayList<>(sortedConfigurations.size());
        for (Configuration configuration : sortedConfigurations) {
            configurationDetails.add(ConfigurationDetails.of(configuration));
        }
        return new DependencyReportModel(configurationDetails);
    }

    @Override
    protected void generateReportFor(ProjectDetails project, DependencyReportModel model) {
        for (ConfigurationDetails configuration : model.configurations) {
            renderer.startConfiguration(configuration);
            renderer.render(configuration);
            renderer.completeConfiguration(configuration);
        }
    }

    private Set<Configuration> getReportConfigurations() {
        return configurations != null ? configurations : getNonDeprecatedTaskConfigurations();
    }

    /**
     * Returns the configurations to generate the report for. Defaults to all configurations of this task's containing
     * project.
     *
     * @return the configurations.
     */
    @Internal
    public Set<Configuration> getConfigurations() {
        return configurations;
    }

    /**
     * Sets the configurations to generate the report for.
     *
     * @param configurations The configuration. Must not be null.
     */
    public void setConfigurations(Set<Configuration> configurations) {
        this.configurations = configurations;
    }

    /**
     * Sets the single configuration (by name) to generate the report for.
     *
     * @param configurationName name of the configuration to generate the report for
     */
    @Option(option = "configuration", description = "The configuration to generate the report for.")
    public void setConfiguration(String configurationName) {
        this.configurations = Collections.singleton(ConfigurationFinder.find(getTaskConfigurations(), configurationName));
    }

    private Set<Configuration> getNonDeprecatedTaskConfigurations() {
        Set<Configuration> filteredConfigurations = new HashSet<>();
        for (Configuration configuration : getTaskConfigurations()) {
            if (((ConfigurationInternal)configuration).isDeclarableAgainstByExtension()) {
                filteredConfigurations.add(configuration);
            }
        }
        return filteredConfigurations;
    }

    @Internal
    public abstract ConfigurationContainer getTaskConfigurations();
}
