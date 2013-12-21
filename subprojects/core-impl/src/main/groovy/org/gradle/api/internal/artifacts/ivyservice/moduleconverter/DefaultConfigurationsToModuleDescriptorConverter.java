/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.Configurations;

import java.util.Arrays;

public class DefaultConfigurationsToModuleDescriptorConverter implements ConfigurationsToModuleDescriptorConverter {
    public void addConfigurations(DefaultModuleDescriptor moduleDescriptor, Iterable<? extends Configuration> configurations) {
        for (Configuration configuration : configurations) {
            moduleDescriptor.addConfiguration(getIvyConfiguration(configuration));
        }
    }

    public org.apache.ivy.core.module.descriptor.Configuration getIvyConfiguration(Configuration configuration) {
        String[] superConfigs = Configurations.getNames(configuration.getExtendsFrom(), false).toArray(new String[configuration.getExtendsFrom().size()]);
        Arrays.sort(superConfigs);
        return new org.apache.ivy.core.module.descriptor.Configuration(
                configuration.getName(),
                configuration.isVisible() ? org.apache.ivy.core.module.descriptor.Configuration.Visibility.PUBLIC : org.apache.ivy.core.module.descriptor.Configuration.Visibility.PRIVATE,
                configuration.getDescription(),
                superConfigs,
                configuration.isTransitive(),
                null);
    }
}
