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

package org.gradle.api.tasks.diagnostics.internal.configurations.renderer;

import org.gradle.api.tasks.diagnostics.internal.configurations.model.ConfigurationReportModel;
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.AbstractConfigurationReportSpec;
import org.gradle.reporting.ReportRenderer;

/**
 * An {@code abstract} {@link ReportRenderer} implementation that can be used to render a {@link ConfigurationReportModel}
 * according to a {@link AbstractConfigurationReportSpec}.
 *
 * This is meant to be the base class for any such renderer used for configuration reporting.
 *
 * @param <E> the destination type which will receive the model data and be responsible for writing it somewhere
 */
public abstract class AbstractConfigurationReportRenderer<E> extends ReportRenderer<ConfigurationReportModel, E> {
    protected final AbstractConfigurationReportSpec spec;

    protected AbstractConfigurationReportRenderer(AbstractConfigurationReportSpec spec) {
        this.spec = spec;
    }
}
