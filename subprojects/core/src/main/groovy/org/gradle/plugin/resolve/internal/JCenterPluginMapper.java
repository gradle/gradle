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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.Supplier;

import java.util.List;

public class JCenterPluginMapper implements ModuleMappingPluginResolver.Mapper {

    public static final String BINTRAY_API_OVERRIDE_URL_PROPERTY = JCenterPluginMapper.class.getName() + ".bintray.override";

    public static final String GRADLE_PLUGINS_ORG = "gradle-plugins-development";
    public static final String GRADLE_PLUGINS_REPO = "gradle-plugins";
    public static final String PLUGIN_ID_ATTRIBUTE_NAME = "gradle-plugin-id";

    private static final String NOT_FOUND = "";

    private final Supplier<PersistentIndexedCache<PluginRequest, String>> cacheSupplier;

    public JCenterPluginMapper(Supplier<PersistentIndexedCache<PluginRequest, String>> cacheSupplier) {
        this.cacheSupplier = cacheSupplier;
    }

    public Dependency map(final PluginRequest request, DependencyHandler dependencyHandler) {
        final String pluginId = request.getId();

        String systemId = cacheSupplier.supplyTo(new Transformer<String, PersistentIndexedCache<PluginRequest, String>>() {
            public String transform(PersistentIndexedCache<PluginRequest, String> cache) {
                return doCacheAwareSearch(request, pluginId, cache);
            }
        });

        if (systemId.equals(NOT_FOUND)) {
            return null;
        } else {
            return dependencyHandler.create(systemId + ":" + request.getVersion());
        }
    }

    private String doCacheAwareSearch(PluginRequest request, String pluginId, PersistentIndexedCache<PluginRequest, String> indexedCache) {
        String cached = indexedCache.get(request);
        if (cached != null) {
            return cached;
        }

        Bintray bintrayClient = createBintrayClient();
        List<Pkg> results = bintrayClient.
                subject(GRADLE_PLUGINS_ORG).
                repository(GRADLE_PLUGINS_REPO).
                searchForPackage().
                byAttributeName(PLUGIN_ID_ATTRIBUTE_NAME).
                equals(pluginId).
                search();

        String systemId;

        if (results.isEmpty()) {
            systemId = NOT_FOUND;
        } else if (request.getVersion() == null) {
            throw new InvalidPluginRequestException(String.format("No version number supplied for plugin '%s'. A version number must be supplied for plugins resolved from '%s'.", pluginId, getBintrayRepoUrl()));
        } else if (results.size() > 1) {
            throw new InvalidPluginRequestException("Found more than one plugin for plugin id " + pluginId);
        } else {
            Pkg pluginPackage = results.get(0);
            List<String> systemIds = pluginPackage.systemIds();
            if (systemIds.isEmpty()) {
                throw new InvalidPluginRequestException("No artifacts in maven layout found for plugin id" + pluginId);
            }

            systemId = systemIds.get(0);
        }

        indexedCache.put(request, systemId);
        return systemId;
    }

    private Bintray createBintrayClient() {
        String override = System.getProperty(BINTRAY_API_OVERRIDE_URL_PROPERTY);
        if (override == null) {
            return BintrayClient.create();
        } else {
            return BintrayClient.create(override, null, null);
        }
    }

    public String getBintrayRepoUrl() {
        return String.format("https://bintray.com/%s/%s", GRADLE_PLUGINS_ORG, GRADLE_PLUGINS_REPO);
    }

}

