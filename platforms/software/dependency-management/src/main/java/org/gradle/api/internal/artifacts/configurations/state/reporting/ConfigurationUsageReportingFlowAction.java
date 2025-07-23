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

public abstract class ConfigurationUsageReportingFlowAction implements FlowAction<ConfigurationUsageReportingFlowAction.Parameters> {
    private static final Logger logger = Logging.getLogger(ConfigurationUsageReportingFlowAction.class);

    public interface Parameters extends FlowParameters {
        @Input
        Property<ConfigurationUsageService> getConfigurationUsageService();
        @Input
        Property<Boolean> getShowAllUsage();
        @Input // TODO: not really an input, but we need to use @Input to make it available in the flow, as @OutputFile won't work and RegularFileProperty won't work
        Property<File> getReportFile();
    }

    @Override
    public void execute(ConfigurationUsageReportingFlowAction.Parameters parameters) {
        String result = parameters.getConfigurationUsageService().get().reportUsage(parameters.getShowAllUsage().get());
        File reportFile = parameters.getReportFile().get();
        GFileUtils.writeFile(result, reportFile);

        logger.lifecycle("Configuration usage report written to: file:/{}", reportFile.getAbsolutePath());
    }
}
