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

public abstract class ConfigurationUsageReportingFlowAction implements FlowAction<ConfigurationUsageReportingFlowAction.Parameters> {
    private static final Logger logger = Logging.getLogger(ConfigurationUsageReportingFlowAction.class);

    public interface Parameters extends FlowParameters {
        @Input
        Property<ConfigurationUsageService> getConfigurationUsageService();
    }

    @Override
    public void execute(ConfigurationUsageReportingFlowAction.Parameters parameters) {
        logger.lifecycle(parameters.getConfigurationUsageService().get().reportUsage());
    }
}
