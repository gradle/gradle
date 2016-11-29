/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.component;

import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.AttributeContainerInternal;
import org.gradle.api.internal.DefaultAttributeContainer;

class ConfigurationBackedConsumableSoftwareComponent extends AbstractSoftwareComponent implements ConsumableSoftwareComponentInternal {
    private final SoftwareComponent parent;
    private final Configuration configuration;

    public ConfigurationBackedConsumableSoftwareComponent(String name, SoftwareComponent parent, Configuration configuration) {
        super(name);
        this.parent = parent;
        this.configuration = configuration;
    }

    @Override
    public AttributeContainer getMergedAttributes() {
        AttributeContainerInternal merged = parent != null ? ((AttributeContainerInternal) parent.getAttributes()).copy() : new DefaultAttributeContainer();
        for (Attribute attribute : getAttributes().keySet()) {
            merged.attribute(attribute, getAttributes().getAttribute(attribute));
        }
        return merged.asImmutable();
    }

    @Override
    public void dependency(Object notation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void artifact(Object notation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getConfigurationName() {
        return configuration.getName();
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public DependencySet getDependencies() {
        return configuration.getAllDependencies();
    }

    @Override
    public PublishArtifactSet getArtifacts() {
        return configuration.getAllArtifacts();
    }
}
