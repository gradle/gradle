/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultBuildConfigurer implements BuildConfigurer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildConfigurer.class);
    private final ProjectConfigurer projectConfigurer;

    public DefaultBuildConfigurer(ProjectConfigurer projectConfigurer) {
        this.projectConfigurer = projectConfigurer;
    }

    public void configure(GradleInternal gradle) {
        maybeInformAboutIncubatingMode(gradle);
        if (gradle.getStartParameter().isConfigureOnDemand()) {
            projectConfigurer.configure(gradle.getRootProject());
        } else {
            projectConfigurer.configureHierarchy(gradle.getRootProject());
        }
    }

    private void maybeInformAboutIncubatingMode(GradleInternal gradle) {
        StartParameter startParameter = gradle.getStartParameter();

        if (startParameter.isParallelProjectExecutionEnabled() && startParameter.isConfigureOnDemand()) {
            SingleMessageLogger.incubatingFeatureUsed("Parallel execution with configuration on demand");
        } else if (startParameter.isParallelProjectExecutionEnabled()) {
            SingleMessageLogger.incubatingFeatureUsed("Parallel execution");
        } else if (startParameter.isConfigureOnDemand()) {
            SingleMessageLogger.incubatingFeatureUsed("Configuration on demand");
        }
    }
}
