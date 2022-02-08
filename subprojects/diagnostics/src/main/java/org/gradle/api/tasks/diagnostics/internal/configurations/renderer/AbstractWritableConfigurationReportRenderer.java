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

import java.io.Writer;

/**
 * An {@code abstract} {@link AbstractConfigurationReportRenderer} extension that can be used to render a {@link ConfigurationReportModel}
 * to a {@link Writer}.
 *
 * This is meant to be the base class for any such renderer which does <strong>NOT</strong> output directly to the console.
 */
public abstract class AbstractWritableConfigurationReportRenderer extends AbstractConfigurationReportRenderer<Writer> {
    public AbstractWritableConfigurationReportRenderer(AbstractConfigurationReportSpec spec) {
        super(spec);
    }
}
