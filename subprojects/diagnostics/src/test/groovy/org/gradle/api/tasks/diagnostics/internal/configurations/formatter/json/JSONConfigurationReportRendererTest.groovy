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

package org.gradle.api.tasks.diagnostics.internal.configurations.formatter.json

import com.google.gson.Gson
import com.google.gson.GsonBuilder

import org.gradle.api.tasks.diagnostics.internal.configurations.model.ConfigurationReportModel
import org.gradle.api.tasks.diagnostics.internal.configurations.model.ReportConfiguration
import org.gradle.api.tasks.diagnostics.internal.configurations.renderer.json.JSONConfigurationReport
import org.gradle.api.tasks.diagnostics.internal.configurations.renderer.json.JSONConfigurationReportRenderer
import org.gradle.api.tasks.diagnostics.internal.configurations.spec.OutgoingVariantsSpec
import spock.lang.Specification

import static java.util.Collections.singletonList

class JSONConfigurationReportRendererTest extends Specification {
    private final spec = new OutgoingVariantsSpec(null, false)
    private final renderer = new JSONConfigurationReportRenderer(spec)
    private final StringWriter output = new StringWriter()
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    def "empty model produces empty output"() {
        given:
        def model = new ConfigurationReportModel("myLib", Collections.emptyList(), Collections.emptyList(), Collections.emptyList())

        when:
        renderer.render(model, output)

        then:
        def expected = expectedJson("""{ "configurations": [] }""")
        output.toString().trim() == expected
    }

    def "model with single eligible config produces report"() {
        given:
        def model = new ConfigurationReportModel("myLib",
            singletonList(new ReportConfiguration("test", "a test config", ReportConfiguration.Type.RESOLVABLE, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),)),
            Collections.emptyList(), Collections.emptyList())

        when:
        renderer.render(model, output)

        then:
        def expected = expectedJson("""{ "configurations": [
            {
                "name": "test",
                "description": "a test config",
                "type": "RESOLVABLE",
                "attributes": [],
                "capabilities": [],
                "artifacts": [],
                "variants": [],
                "extendedConfigurations": []
            }
        ] }""")
        output.toString().trim() == expected
    }

    private String expectedJson(String rawJson) {
        return gson.toJson(gson.fromJson(rawJson, JSONConfigurationReport)).trim()
    }
}
