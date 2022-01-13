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

package org.gradle.api.tasks.diagnostics.internal.variantreports.model;

import java.util.List;

public final class VariantReportModel {
    private final String projectName;
    private final List<ReportConfiguration> matchingConfigs;
    private final List<ReportConfiguration> allConfigs;

    public VariantReportModel(String projectName, List<ReportConfiguration> matchingConfigs, List<ReportConfiguration> allConfigs) {
        this.projectName = projectName;
        this.matchingConfigs = matchingConfigs;
        this.allConfigs = allConfigs;
    }

    public String getProjectName() {
        return projectName;
    }

    public List<ReportConfiguration> getMatchingConfigs() {
        return matchingConfigs;
    }

    public List<ReportConfiguration> getAllConfigs() {
        return allConfigs;
    }
}
