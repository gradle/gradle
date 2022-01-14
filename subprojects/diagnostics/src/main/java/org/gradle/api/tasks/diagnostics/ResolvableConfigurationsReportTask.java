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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.diagnostics.internal.configurations.formatter.ConfigurationReportSpec;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

import java.util.function.Predicate;

/**
 * A task which reports the requested variants of a project on the command line.
 *
 * This is useful for determining which attributes are associated with the resolvable
 * configurations being used to resolve a project's dependencies.
 *
 * Variants, in this context, means "resolvable configurations".
 *
 * @since 7.5
 */
@Incubating
@DisableCachingByDefault(because = "Produces only non-cacheable console output by examining configurations at execution time")
public class ResolvableConfigurationsReportTask extends AbstractConfigurationReportTask {
    private final Property<String> configurationSpec = getProject().getObjects().property(String.class);
    private final Property<Boolean> showAll = getProject().getObjects().property(Boolean.class).convention(false);

    @Input
    @Optional
    @Option(option = "configuration", description = "The requested configuration name")
    public Property<String> getConfigurationName() {
        return configurationSpec;
    }

    @Input
    @Optional
    @Option(option = "all", description = "Shows all resolvable configurations, including legacy and deprecated configurations")
    public Property<Boolean> getShowAll() {
        return showAll;
    }

    @Override
    protected ConfigurationReportSpec buildReportSpec() {
        return new ConfigurationReportSpec(ConfigurationReportSpec.ReportType.RESOLVABLE, configurationSpec.getOrNull(), showAll.get());
    }

    @Override
    protected Predicate<Configuration> buildEligibleConfigurationsFilter() {
        return Configuration::isCanBeResolved;
    }
}
