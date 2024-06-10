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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationFinder;
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.ReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.work.DisableCachingByDefault;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Displays the dependency tree for a configuration.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class AbstractDependencyReportTask extends AbstractProjectBasedReportTask<AbstractDependencyReportTask.DependencyReportModel> {

    private DependencyReportRenderer renderer = new AsciiDependencyReportRenderer();

    public AbstractDependencyReportTask() {
        getConfigurations().convention(getConfiguration()
            .map(Collections::singleton)
            .orElse(getProject().provider(this::getNonDeprecatedTaskConfigurations))
        );
    }

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

    @Override
    protected DependencyReportModel calculateReportModelFor(Project project) {
        return new DependencyReportModel(getConfigurations().get().stream()
            .sorted(Comparator.naturalOrder())
            .map(configurationName -> ConfigurationFinder.find(project.getConfigurations(), configurationName))
            .map(ConfigurationDetails::of)
            .collect(ImmutableList.toImmutableList()));
    }

    @Override
    protected void generateReportFor(ProjectDetails project, DependencyReportModel model) {
        for (ConfigurationDetails configuration : model.configurations) {
            renderer.startConfiguration(configuration);
            renderer.render(configuration);
            renderer.completeConfiguration(configuration);
        }
    }

    /**
     * Returns the configurations (by name) to generate the report for. Defaults to all configurations of this task's containing
     * project.
     *
     * @return the configurations.
     */
    @Internal
    @ReplacesEagerProperty(adapter = GetConfigurationsAdapter.class)
    abstract public SetProperty<String> getConfigurations();

    /**
     * Sets the single configuration (by name) to generate the report for.
     *
     * @since 8.9
     */
    @Option(option = "configuration", description = "The configuration to generate the report for.")
    @Internal
    @ReplacesEagerProperty(replacedAccessors = @ReplacedAccessor(value = AccessorType.SETTER, name = "setConfiguration"))
    abstract public Property<String> getConfiguration();

    private Set<String> getNonDeprecatedTaskConfigurations() {
        Set<String> filteredConfigurations = new HashSet<>();
        for (Configuration configuration : getTaskConfigurations()) {
            if (((ConfigurationInternal) configuration).isDeclarableByExtension()) {
                filteredConfigurations.add(configuration.getName());
            }
        }
        return filteredConfigurations;
    }

    @Internal
    public abstract ConfigurationContainer getTaskConfigurations();

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

    static class GetConfigurationsAdapter {
        @BytecodeUpgrade
        static Set<Configuration> getConfigurations(AbstractDependencyReportTask self) {
            return self.getConfigurations()
                .map(configurations -> configurations.stream()
                    .map(configurationName -> ConfigurationFinder.find(self.getProject().getConfigurations(), configurationName))
                    .collect(ImmutableSet.toImmutableSet()))
                .get();
        }

        @BytecodeUpgrade
        static void setConfigurations(AbstractDependencyReportTask self, Set<Configuration> configurations) {
            self.getConfigurations().set(configurations.stream()
                .map(Configuration::getName)
                .collect(ImmutableSet.toImmutableSet()));
        }
    }
}
