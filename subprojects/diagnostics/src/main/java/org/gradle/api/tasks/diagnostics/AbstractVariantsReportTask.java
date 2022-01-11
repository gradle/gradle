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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.variantreports.formatter.AbstractVariantReportWriter;
import org.gradle.api.tasks.diagnostics.internal.variantreports.model.ReportConfiguration;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
    protected final Property<String> format = getProject().getObjects().property(String.class).convention("text");

    @Input
    @org.gradle.api.tasks.Optional
    @Option(option = "format", description = "The output format (text, json), defaults to text")
    Property<String> getFormat() { return format; }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Internal protected abstract Optional<String> getSearchTarget();
    @Internal protected abstract AbstractVariantReportWriter getReportWriter();
    @Internal protected abstract Predicate<Configuration> getMatchingConfigurationsFilter();
    @Internal protected abstract Predicate<Configuration> getAllConfigurationsFilter();

    @TaskAction
    public void report() {
        getReportWriter().writeReport(
            getSearchTarget(),
            gatherConfigurationData(getMatchingConfigurationsFilter()),
            gatherConfigurationData(getAllConfigurationsFilter()));
    }

    private List<ReportConfiguration> gatherConfigurationData(Predicate<Configuration> filter) {
        return getProject().getConfigurations()
            .stream()
            .filter(filter)
            .sorted(Comparator.comparing(Configuration::getName))
            .map(c -> ReportConfiguration.fromConfigurationInProject(c, getProject(), getFileResolver()))
            .collect(Collectors.toList());
    }
}
