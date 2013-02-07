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

package org.gradle.api.publish.ivy.internal;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.Module;
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
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.*;

public class DefaultIvyPublication implements IvyPublicationInternal {

    private final String name;
    private final IvyModuleDescriptorInternal descriptor;
    private final Module module;
    private final IvyConfigurationContainer configurations;
    private FileCollection descriptorFile;
    private SoftwareComponentInternal component;

    public DefaultIvyPublication(
            String name, Instantiator instantiator, Module module, NotationParser<IvyArtifact> ivyArtifactParser
    ) {
        this.name = name;
        this.module = module;
        configurations = instantiator.newInstance(DefaultIvyConfigurationContainer.class, ivyArtifactParser, instantiator);
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

        IvyConfiguration runtimeConfiguration = getConfiguration("runtime");
        for (PublishArtifact publishArtifact : this.component.getArtifacts()) {
            runtimeConfiguration.artifact(publishArtifact);
        }

        IvyConfiguration defaultConfiguration = getConfiguration("default");
        defaultConfiguration.extend(runtimeConfiguration);
    }

    private IvyConfiguration getConfiguration(String name) {
        IvyConfiguration configuration = configurations.findByName(name);
        if (configuration != null) {
            return configuration;
        }
        return configurations.create(name);
    }

    public void configurations(Action<? super IvyConfigurationContainer> action) {
        action.execute(configurations);
    }

    public IvyConfigurationContainer getConfigurations() {
        return configurations;
    }

    public FileCollection getPublishableFiles() {
        final List<FileCollection> allFiles = new ArrayList<FileCollection>();
        configurations.withType(DefaultIvyConfiguration.class).all(new Action<DefaultIvyConfiguration>() {
            public void execute(DefaultIvyConfiguration ivyConfiguration) {
                allFiles.add(ivyConfiguration.getArtifacts().getFiles());
            }
        });
        allFiles.add(descriptorFile);
        return new UnionFileCollection(allFiles);
    }

    public Module getModule() {
        return module;
    }

    public Set<Dependency> getRuntimeDependencies() {
        return component == null ? Collections.<Dependency>emptySet() : component.getRuntimeDependencies();
    }

    public IvyNormalizedPublication asNormalisedPublication() {
        final Set<IvyArtifact> allArtifacts = new LinkedHashSet<IvyArtifact>();
        configurations.withType(DefaultIvyConfiguration.class).all(new Action<DefaultIvyConfiguration>() {
            public void execute(DefaultIvyConfiguration ivyConfiguration) {
                allArtifacts.addAll(ivyConfiguration.getArtifacts());
            }
        });
        IvyNormalizedPublication ivyNormalizedPublication = new IvyNormalizedPublication(name, getModule(), allArtifacts, getDescriptorFile());
        ivyNormalizedPublication.validateArtifacts();
        return ivyNormalizedPublication;
    }

    private File getDescriptorFile() {
        if (descriptorFile == null) {
            throw new IllegalStateException("pomFile not set for publication");
        }
        return descriptorFile.getSingleFile();
    }

}
