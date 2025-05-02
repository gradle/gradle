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

package org.gradle.plugin.use.resolve.internal;

import com.google.common.collect.ImmutableList;
import org.gradle.plugin.management.internal.PluginRequestInternal;

import java.util.List;

public class CompositePluginResolver implements PluginResolver {

    private final List<PluginResolver> repositories;

    public CompositePluginResolver(List<PluginResolver> repositories) {
        this.repositories = repositories;
    }

    @Override
    public PluginResolutionResult resolve(PluginRequestInternal pluginRequest) {
        ImmutableList.Builder<PluginResolutionResult.NotFound> notFoundList = ImmutableList.builder();
        for (PluginResolver repository : repositories) {
            PluginResolutionResult result = repository.resolve(pluginRequest);
            if (result.isFound()) {
                return result;
            }
            notFoundList.addAll(result.getNotFound());
        }
        return PluginResolutionResult.notFound(notFoundList.build());
    }

}
