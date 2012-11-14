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
public class GenerateIvyDescriptor extends DefaultTask {

    private Module module;
    private Set<? extends Configuration> configurations;

    private Action<? super XmlProvider> xmlAction;
    private Object destination;

    private final FileResolver fileResolver;
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final IvyModuleDescriptorWriter ivyModuleDescriptorWriter;

    @Inject
    public GenerateIvyDescriptor(FileResolver fileResolver) {
        this.fileResolver = fileResolver;

        ArtifactPublicationServices publicationServices = getServices().getFactory(ArtifactPublicationServices.class).create();
        moduleDescriptorConverter = publicationServices.getDescriptorFileModuleConverter();
        ivyModuleDescriptorWriter = publicationServices.getIvyModuleDescriptorWriter();

        // Never up to date; we don't understand the data structures.
        getOutputs().upToDateWhen(Specs.satisfyNone());
    }

    public Module getModule() {
        return module;
    }

    public void setModule(Module module) {
        this.module = module;
    }

    public Set<? extends Configuration> getConfigurations() {
        return configurations;
    }

    public void setConfigurations(Set<? extends Configuration> configurations) {
        this.configurations = configurations;
    }

    @OutputFile
    public File getDestination() {
        return destination == null ? null : fileResolver.resolve(destination);
    }

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

        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(getConfigurations(), getModule());
        ivyModuleDescriptorWriter.write(moduleDescriptor, getDestination(), xmlTransformer);
    }
}
