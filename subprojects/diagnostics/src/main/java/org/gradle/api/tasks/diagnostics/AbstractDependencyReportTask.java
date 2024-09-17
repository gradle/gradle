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
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationFinder;
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.dependencies.AsciiDependencyReportRenderer;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.serialization.Transient;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Displays the dependency tree for a configuration.
 */
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class AbstractDependencyReportTask extends AbstractProjectBasedReportTask<AbstractDependencyReportTask.DependencyReportModel> {

    private final Transient<SetProperty<Configuration>> configurations = Transient.of(getObjectFactory().setProperty(Configuration.class));

    public AbstractDependencyReportTask() {
        getRenderer().convention(new AsciiDependencyReportRenderer()).finalizeValueOnRead();
        getConfigurations().add(getSelectedConfiguration().map(name -> ConfigurationFinder.find(getTaskConfigurations(), name)));
    }

    @Override
    @ReplacesEagerProperty(replacedAccessors = {
        @ReplacedAccessor(value = ReplacedAccessor.AccessorType.SETTER, name = "setRenderer", originalType = DependencyReportRenderer.class)
    })
    public abstract Property<DependencyReportRenderer> getRenderer();

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
        List<ConfigurationDetails> configurationDetails = getConfigurations()
            .getOrElse(getConfigurationsWithDependencies())
            .stream()
            .sorted(Comparator.comparing(Configuration::getName))
            .map(ConfigurationDetails::of)
            .collect(Collectors.toList());

        return new DependencyReportModel(configurationDetails);
    }

    @Override
    protected void generateReportFor(ProjectDetails project, DependencyReportModel model) {
        DependencyReportRenderer renderer = getRenderer().get();
        for (ConfigurationDetails configuration : model.configurations) {
            renderer.startConfiguration(configuration);
            renderer.render(configuration);
            renderer.completeConfiguration(configuration);
        }
    }

    /**
     * Returns the configurations to generate the report for. Defaults to all configurations of this task's containing
     * project.
     *
     * @return the configurations.
     */
    @Internal
    @ReplacesEagerProperty
    public SetProperty<Configuration> getConfigurations() {
        return Objects.requireNonNull(configurations.get());
    }

    /**
     * The single configuration (by name) to generate the report for.
     *
     * @since 9.0
     */
    @Input
    @Optional
    @Option(option = "configuration", description = "The configuration to generate the report for.")
    public abstract Property<String> getSelectedConfiguration();

    @Deprecated
    public void setConfiguration(String configuration) {
        ProviderApiDeprecationLogger.logDeprecation(AbstractDependencyReportTask.class, "setConfiguration(String)", "getSelectedConfiguration()");
        getSelectedConfiguration().set(configuration);
    }

    private Set<Configuration> getConfigurationsWithDependencies() {
        Set<Configuration> filteredConfigurations = new HashSet<>();
        for (Configuration configuration : getTaskConfigurations()) {
            if (((ConfigurationInternal)configuration).isDeclarableByExtension()) {
                filteredConfigurations.add(configuration);
            }
        }
        return filteredConfigurations;
    }

    @Inject
    protected abstract ConfigurationContainer getTaskConfigurations();
}
