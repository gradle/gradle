/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.AbstractConfigurationReportSpec;
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.ResolvableConfigurationsSpec;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

/**
 * A task which reports the configurations of a project which can be resolved on the command line.
 *
 * This is useful for determining which attributes are associated with the resolvable
 * configurations being used to resolve a project's dependencies.  The output can help predict which variant of
 * each dependency will be resolved.
 *
 * @since 7.5
 */
@Incubating
@DisableCachingByDefault(because = "Produces only non-cacheable console output by examining configurations at execution time")
public abstract class ResolvableConfigurationsReportTask extends AbstractConfigurationReportTask {
    /**
     * Limits the report to a single configuration.
     *
     * @return property holding name of the configuration to report
     */
    @Input
    @Optional
    @Option(option = "configuration", description = "The name of the configuration to report")
    public abstract Property<String> getConfigurationName();

    /**
     * Shows all configurations, including legacy and deprecated configurations.
     *
     * @return property holding the flag to show all configurations
     */
    @Input
    @Optional
    @Option(option = "all", description = "Shows all resolvable configurations, including legacy and deprecated configurations")
    public abstract Property<Boolean> getShowAll();

    /**
     * Show all extended configurations, including transitively extended configurations.
     *
     * @return property holding the flag to show all extended configurations
     */
    @Input
    @Optional
    @Option(option = "recursive", description = "Lists all extended configurations of the reported configurations, including any which are extended transitively")
    public abstract Property<Boolean> getRecursive();

    @Override
    protected AbstractConfigurationReportSpec buildReportSpec() {
        return new ResolvableConfigurationsSpec(getConfigurationName().getOrNull(), getShowAll().get(), getRecursive().get());
    }
}
