/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.AbstractConfigurationReportSpec;
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.OutgoingVariantsSpec;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

import java.util.function.Predicate;

/**
 * A task which reports the outgoing variants of a project on the command line.
 * This is useful for listing what a project produces in terms of variants and
 * what artifacts are attached to each variant.
 * Variants, in this context, must be understood as "things produced by a project
 * which can safely be consumed by another project".
 *
 * @since 6.0
 */
@DisableCachingByDefault(because = "Produces only non-cacheable console output by examining configurations at execution time")
public abstract class OutgoingVariantsReportTask extends AbstractConfigurationReportTask {
    @Input
    @Optional
    @Option(option = "variant", description = "The variant name")
    public abstract Property<String> getVariantName();

    @Input
    @Optional
    @Option(option = "all", description = "Shows all variants, including legacy and deprecated configurations")
    public abstract Property<Boolean> getShowAll();

    @Override
    protected AbstractConfigurationReportSpec buildReportSpec() {
        return new OutgoingVariantsSpec(getVariantName().getOrNull(), getShowAll().get());
    }

    @Override
    protected Predicate<Configuration> buildEligibleConfigurationsFilter() {
        return Configuration::isCanBeConsumed;
    }
}
