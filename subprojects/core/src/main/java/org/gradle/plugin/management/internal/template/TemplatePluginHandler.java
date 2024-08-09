/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.management.internal.template;

import org.gradle.api.NonNullApi;
import org.gradle.configuration.TemplatePluginRequest;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@NonNullApi
public final class TemplatePluginHandler {
    public static final String TEMPLATE_PLUGINS_PROP = "org.gradle.internal.buildinit.templates.plugins";

    public static PluginRequests getTemplatePlugins() {
        String propValue = System.getProperty(TEMPLATE_PLUGINS_PROP);
        if (propValue == null) {
            return PluginRequests.EMPTY;
        } else {
            String[] pluginRequests = propValue.split(",");
            List<PluginRequestInternal> templatePluginRequests = Arrays.stream(pluginRequests)
                .map(TemplatePluginRequest::parsePluginRequest)
                .collect(Collectors.toList());
            return PluginRequests.of(templatePluginRequests);
        }
    }
}
