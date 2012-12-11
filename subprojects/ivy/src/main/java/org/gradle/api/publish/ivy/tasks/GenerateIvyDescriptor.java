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
import org.gradle.api.*;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.artifacts.ivyservice.IvyModuleDescriptorWriter;
import org.gradle.api.internal.artifacts.ivyservice.ModuleDescriptorConverter;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.xml.XmlTransformer;
import org.gradle.api.publish.ivy.IvyModuleDescriptor;
import org.gradle.api.publish.ivy.internal.IvyModuleDescriptorInternal;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskDependency;

import javax.inject.Inject;
import java.io.File;
import java.util.Date;

/**
 * Generates an Ivy XML Module Descriptor file.
 *
 * @since 1.4
 */
@Incubating
public class GenerateIvyDescriptor extends DefaultTask {

    private IvyModuleDescriptor descriptor;
    private final PublishArtifact descriptorArtifact;

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

        this.descriptorArtifact = new IvyDescriptorArtifact();
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

    public Action<? super XmlProvider> getXmlAction() {
        return xmlAction;
    }

    public void setXmlAction(Action<? super XmlProvider> xmlAction) {
        this.xmlAction = xmlAction;
    }

    public PublishArtifact getDescriptorArtifact() {
        return descriptorArtifact;
    }

    @TaskAction
    public void doGenerate() {
        XmlTransformer xmlTransformer = new XmlTransformer();
        Action<? super XmlProvider> xmlAction = getXmlAction();
        if (xmlAction != null) {
            xmlTransformer.addAction(xmlAction);
        }

        IvyModuleDescriptorInternal descriptorInternal = toIvyModuleDescriptorInternal(getDescriptor());

        ModuleDescriptorConverter moduleDescriptorConverter = publicationServices.getDescriptorFileModuleConverter();
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(descriptorInternal.getConfigurations(), descriptorInternal.getModule());
        IvyModuleDescriptorWriter ivyModuleDescriptorWriter = publicationServices.getIvyModuleDescriptorWriter();
        ivyModuleDescriptorWriter.write(moduleDescriptor, getDestination(), xmlTransformer);
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

    private class IvyDescriptorArtifact implements PublishArtifact {
        private final DefaultTaskDependency dependency;

        public IvyDescriptorArtifact() {
            this.dependency = new DefaultTaskDependency();
            this.dependency.add(GenerateIvyDescriptor.this);
        }

        public String getName() {
            return "ivy";
        }

        public String getExtension() {
            return "xml";
        }

        public String getType() {
            return "xml";
        }

        public String getClassifier() {
            return null;
        }

        public File getFile() {
            return getDestination();
        }

        public Date getDate() {
            return null;
        }

        public TaskDependency getBuildDependencies() {
            return dependency;
        }
    }
}
