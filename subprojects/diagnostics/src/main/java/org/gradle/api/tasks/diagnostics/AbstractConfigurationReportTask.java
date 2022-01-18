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
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.configurations.formatter.TextConfigurationReportWriter;
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.AbstractConfigurationReportSpec;
import org.gradle.api.tasks.diagnostics.internal.configurations.formatter.ConfigurationReportWriter;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportConfiguration;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ConfigurationReportModel;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
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
public abstract class AbstractConfigurationReportTask extends DefaultTask {
    @Input
    @org.gradle.api.tasks.Optional
    @Option(option = "format", description = "The output format (text, json), defaults to text")
    public abstract Property<String> getFormat();

    @Inject
    protected abstract StyledTextOutputFactory getTextOutputFactory();

    @Inject
    protected abstract FileResolver getFileResolver();

    protected abstract AbstractConfigurationReportSpec buildReportSpec();
    protected abstract Predicate<Configuration> buildEligibleConfigurationsFilter();

    @TaskAction
    public final void report() {
        final AbstractConfigurationReportSpec reportSpec = buildReportSpec();
        final ConfigurationReportModel reportModel = buildReportModel();
        final ConfigurationReportWriter writer = buildReportWriter();
        writer.writeReport(reportSpec, reportModel);
    }

    private ConfigurationReportModel buildReportModel() {
        return new ConfigurationReportModel(
            getProject().getName(),
            gatherConfigurationData(buildEligibleConfigurationsFilter()));
    }

    private ConfigurationReportWriter buildReportWriter() {
        switch (getFormat().get()) {
            case "text":
                return new TextConfigurationReportWriter(getTextOutputFactory().create(getClass()));
            default:
                throw new IllegalArgumentException("Unknown format: " + getFormat().get());
        }
    }

    private List<ReportConfiguration> gatherConfigurationData(Predicate<Configuration> filter) {
        return getProject().getConfigurations()
            .stream()
            .filter(filter)
            .sorted(Comparator.comparing(Configuration::getName))
            .map(ConfigurationInternal.class::cast)
            .map(c -> ReportConfiguration.fromConfigurationInProject(c, getProject(), getFileResolver()))
            .collect(Collectors.toList());
    }
}
