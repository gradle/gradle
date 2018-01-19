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

import org.gradle.api.initialization.definition.InjectedPluginDependencies;
import org.gradle.api.initialization.definition.InjectedPluginDependency;
import org.gradle.plugin.management.internal.DefaultPluginRequests;
import org.gradle.plugin.management.internal.PluginRequests;

public class DefaultInjectedPluginDependencies implements InjectedPluginDependencies {
    @Override
    public InjectedPluginDependency id(String id) {
        // TODO: Record plugins requested
        return new DefaultInjectedPluginDependency(id);
    }

    /**
     * TODO: Capture context (classloader scope) and turn these into SelfResolvingPluginRequest
     */
    public PluginRequests getRequests() {
        return DefaultPluginRequests.EMPTY;
    }
}
