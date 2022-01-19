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

package org.gradle.api.tasks.diagnostics.internal.configurations.formatter.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.tasks.diagnostics.internal.configurations.formatter.ConfigurationReportWriter;
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ConfigurationReportModel;
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.AbstractConfigurationReportSpec;
import org.gradle.internal.logging.text.StyledTextOutput;

public class JSONConfigurationReportWriter implements ConfigurationReportWriter {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void writeReport(StyledTextOutput output, AbstractConfigurationReportSpec spec, ConfigurationReportModel data) {
        String json = gson.toJson(new JSONConfigurationReport(data.getEligibleConfigs()));
        output.println(json);
    }
}
