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
package org.gradle.internal.component;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata;

import java.util.Set;

public class LegacyConfigurationsSupplier implements Supplier<ImmutableList<? extends ConfigurationGraphResolveMetadata>> {
    private final ComponentGraphResolveMetadata targetComponent;

    public LegacyConfigurationsSupplier(ComponentGraphResolveMetadata targetComponent) {
        this.targetComponent = targetComponent;
    }

    @Override
    public ImmutableList<? extends ConfigurationGraphResolveMetadata> get() {
        Set<String> configurationNames = targetComponent.getConfigurationNames();
        ImmutableList.Builder<ConfigurationGraphResolveMetadata> builder = new ImmutableList.Builder<>();
        for (String configurationName : configurationNames) {
            ConfigurationGraphResolveMetadata configuration = targetComponent.getConfiguration(configurationName);
            if (configuration.isCanBeConsumed()) {
                builder.add(configuration);
            }
        }
        return builder.build();
    }
}
