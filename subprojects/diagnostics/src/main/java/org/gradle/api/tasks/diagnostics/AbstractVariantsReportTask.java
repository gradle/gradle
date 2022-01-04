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

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Base class for tasks which reports on attributes of a variant or configuration.
 *
 * @since 7.5
 */
@Incubating
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class AbstractVariantsReportTask extends DefaultTask {
    @Internal
    protected abstract Predicate<Configuration> getConfigurationsToReportFilter();

    @Internal
    protected List<Configuration> getConfigurationsToReport() {
        return getConfigurations(getConfigurationsToReportFilter());
    }

    protected List<Configuration> getConfigurations(Predicate<Configuration> filter) {
        return getProject().getConfigurations()
            .stream()
            .filter(filter)
            .sorted(Comparator.comparing(Configuration::getName))
            .collect(Collectors.toList());
    }
}
