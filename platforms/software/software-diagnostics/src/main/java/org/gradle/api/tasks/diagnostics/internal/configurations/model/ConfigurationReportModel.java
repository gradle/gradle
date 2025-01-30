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

package org.gradle.api.tasks.diagnostics.internal.configurations.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

 /**
 * Lightweight, immutable model of all the data in a project necessary for configuration reporting.
  *
  * The intended use is that this data model can be populated with the complete information of a project prior to any
  * report logic running.  This enables the reporting logic to remain completely independent of the actual project classes.
 */
public final class ConfigurationReportModel {
    private final String projectName;
    private final List<ReportConfiguration> allConfigs;
    private final ImmutableList<ReportAttribute> attributesWithCompatibilityRules;
    private final ImmutableList<ReportAttribute> attributesWithDisambiguationRules;

    ConfigurationReportModel(String projectName,
                             List<ReportConfiguration> allConfigs,
                             List<ReportAttribute> attributesWithCompatibilityRules,
                             List<ReportAttribute> attributesWithDisambiguationRules) {
        this.projectName = projectName;
        this.allConfigs = allConfigs;
        this.attributesWithCompatibilityRules = ImmutableList.copyOf(attributesWithCompatibilityRules);
        this.attributesWithDisambiguationRules = ImmutableList.copyOf(attributesWithDisambiguationRules);
    }

    public String getProjectName() {
        return projectName;
    }

    public List<ReportAttribute> getAttributesWithCompatibilityRules() {
        return attributesWithCompatibilityRules;
    }

    public List<ReportAttribute> getAttributesWithDisambiguationRules() {
        return attributesWithDisambiguationRules;
    }

    public List<ReportConfiguration> getAllConfigs() {
        return allConfigs;
    }

    public List<ReportConfiguration> getPurelyResolvableConfigs() {
        return allConfigs.stream().filter(ReportConfiguration::isPurelyResolvable).collect(Collectors.toList());
    }

    public List<ReportConfiguration> getPurelyConsumableConfigs() {
        return allConfigs.stream().filter(ReportConfiguration::isPurelyConsumable).collect(Collectors.toList());
    }

    public Optional<ReportConfiguration> getConfigNamed(String configName) {
        return allConfigs.stream().filter(config -> config.getName().equals(configName)).findFirst();
    }
}
