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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.internal.component.local.model.MutableLocalComponentMetaData;

import java.util.Arrays;

public class DefaultConfigurationsToModuleDescriptorConverter implements ConfigurationsToModuleDescriptorConverter {
    public void addConfigurations(MutableLocalComponentMetaData metaData, Iterable<? extends Configuration> configurations) {
        for (Configuration configuration : configurations) {
            addConfiguration(metaData, configuration);
        }
    }

    private void addConfiguration(MutableLocalComponentMetaData metaData, Configuration configuration) {
        String[] superConfigs = Configurations.getNames(configuration.getExtendsFrom(), false).toArray(new String[configuration.getExtendsFrom().size()]);
        Arrays.sort(superConfigs);
        metaData.addConfiguration(configuration.getName(), configuration.isVisible(), configuration.getDescription(), superConfigs, configuration.isTransitive());
    }
}
