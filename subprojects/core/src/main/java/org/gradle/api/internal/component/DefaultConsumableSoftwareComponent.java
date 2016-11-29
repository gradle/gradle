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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Attribute;
import org.gradle.api.AttributeContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.AttributeContainerInternal;
import org.gradle.api.internal.DefaultAttributeContainer;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

class DefaultConsumableSoftwareComponent extends AbstractSoftwareComponent implements ConsumableSoftwareComponentInternal {
    private final DefaultDependencySet dependencies;
    private final DefaultPublishArtifactSet artifacts;
    private SoftwareComponent parent;

    public DefaultConsumableSoftwareComponent(String name) {
        super(name);
        dependencies = new DefaultDependencySet("dependencies for '" + name + "'", null, new DefaultDomainObjectSet<Dependency>(Dependency.class));
        // TODO:ADAM - inject
        artifacts = new DefaultPublishArtifactSet("artifacts for '" + name + "'", new DefaultDomainObjectSet<PublishArtifact>(PublishArtifact.class), new DefaultFileCollectionFactory());
    }

    public void setParent(SoftwareComponent parent) {
        this.parent = parent;
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
    public String getConfigurationName() {
        return parent.getName() + StringUtils.capitalize(getName());
    }

    @Override
    public DependencySet getDependencies() {
        return dependencies;
    }

    @Override
    public void dependency(Object notation) {
        String[] parts = notation.toString().split(":");
        dependencies.add(new DefaultExternalModuleDependency(parts[0], parts[1], parts[2]));
    }

    @Override
    public PublishArtifactSet getArtifacts() {
        return artifacts;
    }

    @Override
    public void artifact(Object notation) {
        artifacts.add(new ArchivePublishArtifact((AbstractArchiveTask) notation));
    }
}
