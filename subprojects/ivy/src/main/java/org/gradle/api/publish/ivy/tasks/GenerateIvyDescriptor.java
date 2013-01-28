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

package org.gradle.api.publish.ivy.tasks;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.ivyservice.IvyModuleDescriptorWriter;
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.xml.XmlTransformer;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyModuleDescriptor;
import org.gradle.api.publish.ivy.internal.IvyModuleDescriptorInternal;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskDependency;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import static org.apache.ivy.core.module.descriptor.Configuration.Visibility.PUBLIC;
import static org.gradle.util.CollectionUtils.collectArray;

/**
 * Generates an Ivy XML Module Descriptor file.
 *
 * @since 1.4
 */
@Incubating
public class GenerateIvyDescriptor extends DefaultTask {

    private final FileCollection descriptorFile;

    private IvyModuleDescriptor descriptor;
    private Object destination;

    private final FileResolver fileResolver;
    private final ArtifactPublicationServices publicationServices;

    @Inject
    public GenerateIvyDescriptor(FileResolver fileResolver, ArtifactPublicationServices publicationServices) {
        this.fileResolver = fileResolver;
        this.publicationServices = publicationServices;

        // Never up to date; we don't understand the data structures.
        getOutputs().upToDateWhen(Specs.satisfyNone());

        this.descriptorFile = new DescriptorFileCollection();
    }

    /**
     * The module descriptor metadata.
     *
     * @return The module descriptor.
     */
    public IvyModuleDescriptor getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(IvyModuleDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * The file the descriptor will be written to.
     *
     * @return The file the descriptor will be written to
     */
    @OutputFile
    public File getDestination() {
        return destination == null ? null : fileResolver.resolve(destination);
    }

    /**
     * Sets the destination the descriptor will be written to.
     *
     * The value is resolved with {@link org.gradle.api.Project#file(Object)}
     *
     * @param destination The file the descriptor will be written to.
     */
    public void setDestination(Object destination) {
        this.destination = destination;
    }

    public FileCollection getDescriptorFile() {
        return descriptorFile;
    }

    @TaskAction
    public void doGenerate() {

        IvyModuleDescriptorInternal descriptorInternal = toIvyModuleDescriptorInternal(getDescriptor());

        XmlTransformer xmlTransformer = new XmlTransformer();
        xmlTransformer.addAction(descriptorInternal.getXmlAction());

        // TODO This should use the actual configurations from the publication, not assume runtime.
        IvyDescriptorBuilder ivyDescriptorBuilder = new IvyDescriptorBuilder(publicationServices.getDescriptorFileModuleConverter(), descriptorInternal.getModule());
        for (IvyConfiguration ivyConfiguration : descriptorInternal.getConfigurations()) {
            ivyDescriptorBuilder.addConfiguration(ivyConfiguration);
            for (IvyArtifact ivyArtifact : ivyConfiguration.getArtifacts()) {
                ivyDescriptorBuilder.addArtifact(ivyConfiguration.getName(), ivyArtifact);
            }
        }

        // This assumes the runtime configuration is added, which is always true when there are dependencies (added by component)
        // TODO: Attach dependencies to configuration
        for (Dependency runtimeDependency : descriptorInternal.getRuntimeDependencies()) {
            ivyDescriptorBuilder.addDependency("runtime", runtimeDependency);
        }

        IvyModuleDescriptorWriter ivyModuleDescriptorWriter = publicationServices.getIvyModuleDescriptorWriter();
        ivyModuleDescriptorWriter.write(ivyDescriptorBuilder.build(), getDestination(), xmlTransformer);
    }

    private static IvyModuleDescriptorInternal toIvyModuleDescriptorInternal(IvyModuleDescriptor ivyModuleDescriptor) {
        if (ivyModuleDescriptor == null) {
            return null;
        } else if (ivyModuleDescriptor instanceof IvyModuleDescriptorInternal) {
            return (IvyModuleDescriptorInternal) ivyModuleDescriptor;
        } else {
            throw new InvalidUserDataException(
                    String.format(
                            "ivyModuleDescriptor implementations must implement the '%s' interface, implementation '%s' does not",
                            IvyModuleDescriptorInternal.class.getName(),
                            ivyModuleDescriptor.getClass().getName()
                    )
            );
        }
    }

    private class DescriptorFileCollection extends AbstractFileCollection {
        private final DefaultTaskDependency dependency;

        public DescriptorFileCollection() {
            this.dependency = new DefaultTaskDependency();
            this.dependency.add(GenerateIvyDescriptor.this);
        }

        @Override
        public String getDisplayName() {
            return "ivy-xml";
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return dependency;
        }

        public Set<File> getFiles() {
            return Collections.singleton(getDestination());
        }
    }

    private static class IvyDescriptorBuilder {
        private final ModuleDescriptorConverter dependencyDescriptorFactory;
        private final DefaultModuleDescriptor moduleDescriptor;

        public IvyDescriptorBuilder(ModuleDescriptorConverter dependencyDescriptorFactory, Module module) {
            this.dependencyDescriptorFactory = dependencyDescriptorFactory;
            moduleDescriptor = new DefaultModuleDescriptor(IvyUtil.createModuleRevisionId(module), module.getStatus(), null);
        }

        public void addConfiguration(IvyConfiguration ivyConfiguration) {
            Set<IvyConfiguration> extendsConfigurations = ivyConfiguration.getExtends();
            int size = extendsConfigurations.size();
            String[] extendsNames = collectArray(extendsConfigurations.toArray(new IvyConfiguration[size]), new String[size], new Transformer<String, IvyConfiguration>() {
                public String transform(IvyConfiguration original) {
                    return original.getName();
                }
            });
            org.apache.ivy.core.module.descriptor.Configuration configuration =
                    new org.apache.ivy.core.module.descriptor.Configuration(ivyConfiguration.getName(), PUBLIC, null, extendsNames, true, null);
            moduleDescriptor.addConfiguration(configuration);
        }

        public void addArtifact(String configuration, IvyArtifact artifact) {
            moduleDescriptor.addArtifact(configuration, createIvyArtifact(artifact, moduleDescriptor.getModuleRevisionId()));
        }

        private Artifact createIvyArtifact(IvyArtifact ivyArtifact, ModuleRevisionId moduleRevisionId) {
            return new DefaultArtifact(
                    moduleRevisionId,
                    null,
                    ivyArtifact.getName(),
                    ivyArtifact.getType(),
                    ivyArtifact.getExtension(),
                    new HashMap<String, String>());
        }

        public void addDependency(String configuration, Dependency dependency) {
            if (dependency instanceof ModuleDependency) {
                dependencyDescriptorFactory.addDependencyDescriptor(configuration, moduleDescriptor, (ModuleDependency) dependency);
            }
        }

        public ModuleDescriptor build() {
            return moduleDescriptor;
        }

    }
}
