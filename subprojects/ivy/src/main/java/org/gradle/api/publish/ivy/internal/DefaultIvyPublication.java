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
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.publish.ivy.IvyModuleDescriptor;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class DefaultIvyPublication implements IvyPublicationInternal {

    private final String name;
    private final IvyModuleDescriptorInternal descriptor;
    private final Module module;
    private FileCollection descriptorFile;
    private SoftwareComponentInternal component;

    public DefaultIvyPublication(
            String name, Instantiator instantiator, Module module
    ) {
        this.name = name;
        this.module = module;
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
            throw new InvalidUserDataException("A MavenPublication cannot include multiple components");
        }
        this.component = (SoftwareComponentInternal) component;
    }

    public FileCollection getPublishableFiles() {
        if (component == null) {
            return descriptorFile;
        }
        return new UnionFileCollection(component.getArtifacts().getFiles(), descriptorFile);
    }

    public Module getModule() {
        return module;
    }

    public Set<Dependency> getRuntimeDependencies() {
        return component == null ? Collections.<Dependency>emptySet() : component.getRuntimeDependencies();
    }

    public IvyNormalizedPublication asNormalisedPublication() {
        // TODO:DAZ Handle missing component
        return new IvyNormalizedPublication(getModule(), component.getArtifacts(), getDescriptorFile());
    }

    private File getDescriptorFile() {
        if (descriptorFile == null) {
            throw new IllegalStateException("pomFile not set for publication");
        }
        return descriptorFile.getSingleFile();
    }

}
