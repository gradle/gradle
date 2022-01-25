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

package org.gradle.api.tasks.diagnostics.internal.configurations;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ConfigurationReportModel;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportAttribute;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportConfiguration;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory for creating {@link ConfigurationReportModel} instances which represent the configurations present in a project.
 */
public final class ConfigurationReportModelFactory {
    private final FileResolver fileResolver;

    public ConfigurationReportModelFactory(FileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    public ConfigurationReportModel buildForProject(Project project) {
        final ConfigurationRuleScanner scanner = new ConfigurationRuleScanner(project);
        final List<ReportAttribute> attributesWithCompatibilityRules = scanner.getAttributesWithCompatibilityRules();
        final List<ReportAttribute> attributesWithDisambiguationRules = scanner.getAttributesWithDisambiguationRules();

        final List<ReportConfiguration> configurations = gather(project);
        populateExtensions(project, configurations);

        return new ConfigurationReportModel(project.getName(), configurations, attributesWithCompatibilityRules, attributesWithDisambiguationRules);
    }

    private List<ReportConfiguration> gather(Project project) {
        return project.getConfigurations()
            .stream()
            .sorted(Comparator.comparing(Configuration::getName))
            .map(ConfigurationInternal.class::cast)
            .map(c -> ReportConfiguration.fromConfigurationInProject(c, project, fileResolver))
            .collect(Collectors.toList());
    }

    private void populateExtensions(Project project, List<ReportConfiguration> converted) {
        project.getConfigurations().forEach(conf -> {
            final ReportConfiguration child = findConverted(conf, converted);
            final List<ReportConfiguration> parents = conf.getExtendsFrom().stream()
                .map(parent -> findConverted(parent, converted))
                .collect(Collectors.toList());
            child.setExtendedConfigurations(parents);
        });
    }

    private ReportConfiguration findConverted(Configuration target, List<ReportConfiguration> converted) {
        return converted.stream().filter(c -> c.getName().equals(target.getName())).findFirst().orElseThrow(IllegalStateException::new);
    }
}
