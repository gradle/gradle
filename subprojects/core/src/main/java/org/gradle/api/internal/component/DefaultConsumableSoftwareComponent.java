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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.component.ConsumableSoftwareComponent;
import org.gradle.api.component.SoftwareComponent;

class DefaultConsumableSoftwareComponent extends AbstractSoftwareComponent implements ConsumableSoftwareComponent {
    private SoftwareComponent parent;
    private Configuration configuration;

    public DefaultConsumableSoftwareComponent(String name) {
        super(name);
    }

    public void setParent(SoftwareComponent parent) {
        this.parent = parent;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
        if (parent != null) {
            for (Attribute attribute : parent.getAttributes().keySet()) {
                configuration.getAttributes().attribute(attribute, parent.getAttributes().getAttribute(attribute));
            }
        }
        for (Attribute attribute : getAttributes().keySet()) {
            configuration.getAttributes().attribute(attribute, getAttributes().getAttribute(attribute));
        }
    }

    @Override
    public DependencySet getDependencies() {
        return configuration.getDependencies();
    }

    @Override
    public PublishArtifactSet getArtifacts() {
        return configuration.getArtifacts();
    }
}
