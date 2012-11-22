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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.XmlTransformer;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.ivyservice.IvyModuleDescriptorWriter;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;

/**
 * Generates an Ivy XML Module Descriptor file.
 */
@Incubating
public class GenerateIvyDescriptor extends DefaultTask {

    private Module module;
    private Set<? extends Configuration> configurations;

    private Action<? super XmlProvider> xmlAction;
    private Object destination;

    private final FileResolver fileResolver;
    private final ArtifactPublicationServices publicationServices;

    @Inject
    public GenerateIvyDescriptor(FileResolver fileResolver, ArtifactPublicationServices publicationServices) {
        this.fileResolver = fileResolver;
        this.publicationServices = publicationServices;

        // Never up to date; we don't understand the data structures.
        getOutputs().upToDateWhen(Specs.satisfyNone());
    }

    /**
     * The module the descriptor represents.
     *
     * @return The module the descriptor represents
     */
    public Module getModule() {
        return module;
    }

    public void setModule(Module module) {
        this.module = module;
    }

    /**
     * The configurations that are part of the descriptor.
     *
     * @return The configurations that are part of the descriptor
     */
    public Set<? extends Configuration> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Set<? extends Configuration> configurations) {
        this.configurations = configurations;
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

    public Action<? super XmlProvider> getXmlAction() {
        return xmlAction;
    }

    public void setXmlAction(Action<? super XmlProvider> xmlAction) {
        this.xmlAction = xmlAction;
    }

    @TaskAction
    public void doGenerate() {
        XmlTransformer xmlTransformer = new XmlTransformer();
        Action<? super XmlProvider> xmlAction = getXmlAction();
        if (xmlAction != null) {
            xmlTransformer.addAction(xmlAction);
        }

        ModuleDescriptorConverter moduleDescriptorConverter = publicationServices.getDescriptorFileModuleConverter();
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(getConfigurations(), getModule());
        IvyModuleDescriptorWriter ivyModuleDescriptorWriter = publicationServices.getIvyModuleDescriptorWriter();
        ivyModuleDescriptorWriter.write(moduleDescriptor, getDestination(), xmlTransformer);
    }
}
