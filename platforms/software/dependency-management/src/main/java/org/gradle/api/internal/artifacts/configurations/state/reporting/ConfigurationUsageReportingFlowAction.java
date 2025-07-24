/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations.state.reporting;

import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.util.Map;

public abstract class ConfigurationUsageReportingFlowAction implements FlowAction<ConfigurationUsageReportingFlowAction.Parameters> {
    private static final Logger LOGGER = Logging.getLogger(ConfigurationUsageReportingFlowAction.class);

    public interface Parameters extends FlowParameters {
        @Input
        Property<ConfigurationUsageService> getConfigurationUsageService();
        @Input
        Property<Boolean> getShowAllUsage();
        @Input // TODO: not really an input, but we need to use @Input to make it available in the flow, as @OutputFile won't work and RegularFileProperty won't work
        Property<File> getReportDir();
    }

    @Override
    public void execute(ConfigurationUsageReportingFlowAction.Parameters parameters) {
        ConfigurationUsageService usageService = parameters.getConfigurationUsageService().get();
        boolean showAllUsage = parameters.getShowAllUsage().get();
        File reportDir = parameters.getReportDir().get();

        writeUsageReport(parameters, usageService, showAllUsage);
        writeLocationsReports(usageService, showAllUsage, reportDir);
    }

    private void writeLocationsReports(ConfigurationUsageService usageService, boolean showAllUsage, File reportDir) {
        usageService.reportUsageLocations(showAllUsage);
        Map<String, String> usageLocationsSummary = usageService.reportUsageLocations(showAllUsage);
        usageLocationsSummary.forEach((location, locationSummary) -> {
            File locationFile = new File(reportDir, location);
            GFileUtils.writeFile(locationSummary, locationFile);
        });
    }

    private void writeUsageReport(Parameters parameters, ConfigurationUsageService usageService, boolean showAllUsage) {
        String usageSummary = usageService.reportUsage(showAllUsage);
        File reportFile = new File(parameters.getReportDir().get(), ConfigurationUsageFeedbackPlugin.REPORT_FILE_NAME);
        GFileUtils.writeFile(usageSummary, reportFile);
        LOGGER.lifecycle("Configuration usage report written to: file://{}", reportFile.getAbsolutePath());
    }
}
