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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ConfigurationReportModel {
    private final String projectName;
    private final List<ReportConfiguration> eligibleConfigs;
    private final List<ReportAttribute> attributesWithCompatibilityRules;
    private final List<ReportAttribute> attributesWithDisambiguationRules;

    public ConfigurationReportModel(String projectName,
                                    List<ReportConfiguration> eligibleConfigs,
                                    List<ReportAttribute> attributesWithCompatibilityRules,
                                    List<ReportAttribute> attributesWithDisambiguationRules) {
        this.projectName = projectName;
        this.eligibleConfigs = eligibleConfigs;
        this.attributesWithCompatibilityRules = attributesWithCompatibilityRules;
        this.attributesWithDisambiguationRules = attributesWithDisambiguationRules;
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

    public List<ReportConfiguration> getEligibleConfigs() {
        return eligibleConfigs;
    }

    public Optional<ReportConfiguration> getConfigNamed(String configName) {
        return eligibleConfigs.stream().filter(config -> config.getName().equals(configName)).findFirst();
    }

    public List<ReportConfiguration> getNonLegacyConfigs() {
        return eligibleConfigs.stream().filter(c -> !c.isLegacy()).collect(Collectors.toList());
    }

}
