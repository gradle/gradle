/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publication;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyConfigurationContainer;
import org.gradle.api.publish.ivy.IvyModuleDescriptor;
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifactSet;
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication;
import org.gradle.api.publish.ivy.internal.publisher.IvyProjectIdentity;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultIvyPublication implements IvyPublicationInternal {

    private final String name;
    private final IvyModuleDescriptorInternal descriptor;
    private final IvyProjectIdentity projectIdentity;
    private final IvyConfigurationContainer configurations;
    private final DefaultIvyArtifactSet ivyArtifacts;
    private final Set<Dependency> runtimeDependencies = new LinkedHashSet<Dependency>();
    private FileCollection descriptorFile;
    private SoftwareComponentInternal component;

    public DefaultIvyPublication(
            String name, Instantiator instantiator, IvyProjectIdentity projectIdentity, NotationParser<IvyArtifact> ivyArtifactNotationParser
    ) {
        this.name = name;
        this.projectIdentity = projectIdentity;
        configurations = instantiator.newInstance(DefaultIvyConfigurationContainer.class, instantiator);
        ivyArtifacts = instantiator.newInstance(DefaultIvyArtifactSet.class, name, ivyArtifactNotationParser);
        descriptor = instantiator.newInstance(DefaultIvyModuleDescriptor.class, this);
    }

    public String getName() {
        return name;
    }

    public IvyModuleDescriptorInternal getDescriptor() {
        return descriptor;
    }

    public void setDescriptorFile(FileCollection descriptorFile) {
        this.descriptorFile = descriptorFile;
    }

    public void descriptor(Action<? super IvyModuleDescriptor> configure) {
        configure.execute(descriptor);
    }

    public void from(SoftwareComponent component) {
        if (this.component != null) {
            throw new InvalidUserDataException(String.format("Ivy publication '%s' cannot include multiple components", name));
        }
        this.component = (SoftwareComponentInternal) component;

        for (PublishArtifact publishArtifact : this.component.getArtifacts()) {
            artifact(publishArtifact).setConf("runtime");
        }

        createConfiguration("runtime");
        createConfiguration("default").extend("runtime");

        runtimeDependencies.addAll(this.component.getRuntimeDependencies());
    }

    private IvyConfiguration createConfiguration(String name) {
        IvyConfiguration configuration = configurations.findByName(name);
        if (configuration != null) {
            return configuration;
        }
        return configurations.create(name);
    }

    public void configurations(Action<? super IvyConfigurationContainer> config) {
        config.execute(configurations);
    }

    public IvyConfigurationContainer getConfigurations() {
        return configurations;
    }

    public IvyArtifact artifact(Object source) {
        return ivyArtifacts.artifact(source);
    }

    public IvyArtifact artifact(Object source, Action<? super IvyArtifact> config) {
        return ivyArtifacts.artifact(source, config);
    }

    public void setArtifacts(Iterable<?> sources) {
        ivyArtifacts.clear();
        for (Object source : sources) {
            artifact(source);
        }
    }

    public DefaultIvyArtifactSet getArtifacts() {
        return ivyArtifacts;
    }

    public FileCollection getPublishableFiles() {
        return new UnionFileCollection(ivyArtifacts.getFiles(), descriptorFile);
    }

    public IvyProjectIdentity getProjectIdentity() {
        return projectIdentity;
    }

    public Set<Dependency> getRuntimeDependencies() {
        return runtimeDependencies;
    }

    public IvyNormalizedPublication asNormalisedPublication() {
        return new IvyNormalizedPublication(name, getProjectIdentity(), getDescriptorFile(), ivyArtifacts);
    }

    private File getDescriptorFile() {
        if (descriptorFile == null) {
            throw new IllegalStateException("descriptorFile not set for publication");
        }
        return descriptorFile.getSingleFile();
    }

}
