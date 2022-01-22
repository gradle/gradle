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

package org.gradle.api.tasks.diagnostics.internal.configurations.renderer.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ConfigurationReportModel;
import org.gradle.api.tasks.diagnostics.internal.configurations.renderer.AbstractWritableConfigurationReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.AbstractConfigurationReportSpec;

import java.io.IOException;
import java.io.Writer;

public final class JSONConfigurationReportRenderer extends AbstractWritableConfigurationReportRenderer {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JSONConfigurationReportRenderer(AbstractConfigurationReportSpec spec) {
        super(spec);
    }

    @Override
    public void render(ConfigurationReportModel data, Writer writer) {
        String json = gson.toJson(new JSONConfigurationReport(data.getAllConfigs()));
        try {
            writer.write(json);
        } catch (IOException e) {
            throw new GradleException("Failed to write report", e);
        }
    }
}
