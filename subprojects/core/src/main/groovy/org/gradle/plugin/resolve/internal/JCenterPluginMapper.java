/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugin.resolve.internal;

import com.jfrog.bintray.client.api.handle.Bintray;
import com.jfrog.bintray.client.api.model.Pkg;
import com.jfrog.bintray.client.impl.BintrayClient;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.util.List;

public class JCenterPluginMapper implements ModuleMappingPluginResolver.Mapper {

    public static final String BINTRAY_API_OVERRIDE_URL_PROPERTY = JCenterPluginMapper.class.getName() + ".bintray.override";

    public static final String GRADLE_PLUGINS_ORG = "gradle-plugins-development";
    public static final String GRADLE_PLUGINS_REPO = "gradle-plugins";
    public static final String PLUGIN_ID_ATTRIBUTE_NAME = "gradle-plugin-id";

    public Dependency map(PluginRequest request, DependencyHandler dependencyHandler) {
        String pluginId = request.getId();
        Bintray bintrayClient = createBintrayClient();
        List<Pkg> results = bintrayClient.
                subject(GRADLE_PLUGINS_ORG).
                repository(GRADLE_PLUGINS_REPO).
                searchForPackage().
                byAttributeName(PLUGIN_ID_ATTRIBUTE_NAME).
                equals(pluginId).
                search();

        if (results.isEmpty()) {
            throw new InvalidPluginRequestException("No plugins found for plugin id " + pluginId);
        }
        if (results.size() > 1) {
            throw new InvalidPluginRequestException("Found more than one plugin for plugin id " + pluginId);
        }
        Pkg pluginPackage = results.get(0);
        List<String> systemIds = pluginPackage.systemIds();
        if (systemIds.isEmpty()) {
            throw new InvalidPluginRequestException("No artifacts in maven layout found for plugin id" + pluginId);
        }
        String version = request.getVersion();
        if (version == null) {
            version = pluginPackage.latestVersion();
        }
        return dependencyHandler.create(systemIds.get(0) + ":" + version);
    }

    private Bintray createBintrayClient() {
        String override = System.getProperty(BINTRAY_API_OVERRIDE_URL_PROPERTY);
        if (override == null) {
            return BintrayClient.create();
        } else {
            return BintrayClient.create(override, null, null);
        }
    }

}

