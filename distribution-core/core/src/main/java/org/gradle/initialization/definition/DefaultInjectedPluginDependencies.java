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

import com.google.common.collect.Lists;
import org.gradle.api.initialization.definition.InjectedPluginDependencies;
import org.gradle.api.initialization.definition.InjectedPluginDependency;

import java.util.List;

public class DefaultInjectedPluginDependencies implements InjectedPluginDependencies {
    private final List<DefaultInjectedPluginDependency> dependencies = Lists.newArrayList();

    @Override
    public InjectedPluginDependency id(String id) {
        DefaultInjectedPluginDependency injectedPluginDependency = new DefaultInjectedPluginDependency(id);
        dependencies.add(injectedPluginDependency);
        return injectedPluginDependency;
    }

    public List<DefaultInjectedPluginDependency> getDependencies() {
        return dependencies;
    }
}
