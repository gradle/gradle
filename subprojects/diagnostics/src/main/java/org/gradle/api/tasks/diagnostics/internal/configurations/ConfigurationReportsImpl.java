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

import org.gradle.api.Task;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.reporting.ConfigurableReport;
import org.gradle.api.reporting.internal.TaskReportContainer;
import org.gradle.api.tasks.diagnostics.configurations.ConfigurationReports;

import javax.inject.Inject;

/**
 * Default implementation of {@link ConfigurationReports} which allows for adding and configuring reports.
 *
 * Class must by non-{@code final}.
 */
public class ConfigurationReportsImpl extends TaskReportContainer<ConfigurableReport> implements ConfigurationReports {
    @Inject
    public ConfigurationReportsImpl(Task task, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(ConfigurableReport.class, task, callbackActionDecorator);
    }
}
