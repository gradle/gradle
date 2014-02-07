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

import java.util.List;

public class CompositePluginResolver implements PluginResolver {

    private final List<PluginResolver> repositories;

    public CompositePluginResolver(List<PluginResolver> repositories) {
        this.repositories = repositories;
    }

    public PluginResolution resolve(PluginRequest pluginRequest) {
        PluginResolution resolution = null;
        for (PluginResolver repository : repositories) {
            resolution = repository.resolve(pluginRequest);
            if (resolution != null) {
                break;
            }
        }

        return resolution;
    }

    public String getDescriptionForNotFoundMessage() {
        StringBuilder sb = new StringBuilder("plugin repositories:");
        for (PluginResolver repository : repositories) {
            sb.append("\n - ").append(repository.getDescriptionForNotFoundMessage());
        }
        return sb.toString();
    }
}
