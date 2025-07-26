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

import org.gradle.api.Plugin;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;

public abstract class ConfigurationUsageFeedbackPlugin implements Plugin<Settings> {
    public static final String REPORT_FILE_NAME = "configuration-usage-report.html";
    public static final String DEFAULT_REPORTS_DIR = "build/reports";

    @Inject
    protected abstract FlowScope getFlowScope();

    @Override
    public void apply(Settings settings) {
        ConfigurationUsageFeedbackExtension extension = settings.getExtensions().create("configurationUsageFeedback", ConfigurationUsageFeedbackExtension.class);
        extension.getShowAllUsage().convention(false);
        extension.getReportDir().convention(settings.getLayout().getRootDirectory().dir(DEFAULT_REPORTS_DIR));

        Provider<ConfigurationUsageService> serviceProvider = settings.getGradle().getSharedServices().registerIfAbsent("configurationUsageService", ConfigurationUsageService.class, spec -> {});

        getFlowScope().always(ConfigurationUsageReportingFlowAction.class, spec -> {
            spec.getParameters().getConfigurationUsageService().set(serviceProvider.get());
            spec.getParameters().getShowAllUsage().set(extension.getShowAllUsage());
            spec.getParameters().getReportDir().set(extension.getReportDir().getAsFile());
        });

        //noinspection CodeBlock2Expr
        settings.getGradle().allprojects(project -> {
            //noinspection CodeBlock2Expr
            project.getConfigurations().configureEach(configuration -> {
                serviceProvider.get().trackConfiguration((ProjectInternal) project, (ConfigurationInternal) configuration);
            });
        });
    }
}
