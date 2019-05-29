/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.initialization.definition;

import org.gradle.api.Transformer;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.management.internal.PluginRequests;

import java.util.List;

import static org.gradle.util.CollectionUtils.collect;

public class InjectedPluginResolver {
    private final ClassLoaderScope classLoaderScope;

    public InjectedPluginResolver(ClassLoaderScope classLoaderScope) {
        this.classLoaderScope = classLoaderScope;
    }

    public PluginRequests resolveAll(List<DefaultInjectedPluginDependency> requests) {
        if (requests.isEmpty()) {
            return DefaultPluginRequests.EMPTY;
        }
        return new DefaultPluginRequests(convert(requests));
    }

    private List<PluginRequestInternal> convert(List<DefaultInjectedPluginDependency> requests) {
        return collect(requests, new Transformer<PluginRequestInternal, DefaultInjectedPluginDependency>() {
            @Override
            public PluginRequestInternal transform(DefaultInjectedPluginDependency original) {
                return new SelfResolvingPluginRequest(original.getId(), classLoaderScope);
            }
        });
    }
}
