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
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.Usage;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.api.publish.internal.ProjectDependencyPublicationResolver;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfigurationContainer;
import org.gradle.api.publish.ivy.IvyModuleDescriptor;
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifactSet;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependency;
import org.gradle.api.publish.ivy.internal.dependency.DefaultIvyDependencySet;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependencyInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationIdentity;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Set;

public class DefaultIvyPublication implements IvyPublicationInternal {

    private final String name;
    private final IvyModuleDescriptorInternal descriptor;
    private final IvyPublicationIdentity publicationIdentity;
    private final IvyConfigurationContainer configurations;
    private final DefaultIvyArtifactSet ivyArtifacts;
    private final DefaultIvyDependencySet ivyDependencies;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private FileCollection descriptorFile;
    private SoftwareComponentInternal component;

    public DefaultIvyPublication(
            String name, Instantiator instantiator, IvyPublicationIdentity publicationIdentity, NotationParser<Object, IvyArtifact> ivyArtifactNotationParser,
            ProjectDependencyPublicationResolver projectDependencyResolver
    ) {
        this.name = name;
        this.publicationIdentity = publicationIdentity;
        this.projectDependencyResolver = projectDependencyResolver;
        configurations = instantiator.newInstance(DefaultIvyConfigurationContainer.class, instantiator);
        ivyArtifacts = instantiator.newInstance(DefaultIvyArtifactSet.class, name, ivyArtifactNotationParser);
        ivyDependencies = instantiator.newInstance(DefaultIvyDependencySet.class);
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

        configurations.maybeCreate("default");

        for (Usage usage : this.component.getUsages()) {
            String conf = usage.getName();
            configurations.maybeCreate(conf);
            configurations.getByName("default").extend(conf);

            for (PublishArtifact publishArtifact : usage.getArtifacts()) {
                artifact(publishArtifact).setConf(conf);
            }

            for (ModuleDependency dependency : usage.getDependencies()) {
                // TODO: When we support multiple components or configurable dependencies, we'll need to merge the confs of multiple dependencies with same id.
                String confMapping = String.format("%s->%s", conf, dependency.getConfiguration());
                if (dependency instanceof ProjectDependency) {
                    addProjectDependency((ProjectDependency) dependency, confMapping);
                } else {
                    addModuleDependency(dependency, confMapping);
                }
            }
        }
    }

    private void addProjectDependency(ProjectDependency dependency, String confMapping) {
        ModuleVersionIdentifier identifier = projectDependencyResolver.resolve(dependency);
        ivyDependencies.add(new DefaultIvyDependency(identifier.getGroup(), identifier.getName(), identifier.getVersion(), confMapping));
    }

    private void addModuleDependency(ModuleDependency dependency, String confMapping) {
        ivyDependencies.add(new DefaultIvyDependency(dependency.getGroup(), dependency.getName(), dependency.getVersion(), confMapping, dependency.getArtifacts()));
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

    public String getOrganisation() {
        return publicationIdentity.getOrganisation();
    }

    public void setOrganisation(String organisation) {
        publicationIdentity.setOrganisation(organisation);
    }

    public String getModule() {
        return publicationIdentity.getModule();
    }

    public void setModule(String module) {
        publicationIdentity.setModule(module);
    }

    public String getRevision() {
        return publicationIdentity.getRevision();
    }

    public void setRevision(String revision) {
        publicationIdentity.setRevision(revision);
    }

    public FileCollection getPublishableFiles() {
        return new UnionFileCollection(ivyArtifacts.getFiles(), descriptorFile);
    }

    public IvyPublicationIdentity getIdentity() {
        return publicationIdentity;
    }

    public Set<IvyDependencyInternal> getDependencies() {
        return ivyDependencies;
    }

    public IvyNormalizedPublication asNormalisedPublication() {
        return new IvyNormalizedPublication(name, getIdentity(), getDescriptorFile(), ivyArtifacts);
    }

    private File getDescriptorFile() {
        if (descriptorFile == null) {
            throw new IllegalStateException("descriptorFile not set for publication");
        }
        return descriptorFile.getSingleFile();
    }

    public ModuleVersionIdentifier getCoordinates() {
        return new DefaultModuleVersionIdentifier(getOrganisation(), getModule(), getRevision());
    }
}
