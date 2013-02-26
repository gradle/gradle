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

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyModuleDescriptor;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependencyInternal;
import org.gradle.api.publish.ivy.internal.publication.IvyModuleDescriptorInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;

/**
 * Generates an Ivy XML Module Descriptor file.
 *
 * @since 1.4
 */
@Incubating
public class GenerateIvyDescriptor extends DefaultTask {

    private IvyModuleDescriptor descriptor;
    private Object destination;

    private final FileResolver fileResolver;

    @Inject
    public GenerateIvyDescriptor(FileResolver fileResolver) {
        this.fileResolver = fileResolver;

        // Never up to date; we don't understand the data structures.
        getOutputs().upToDateWhen(Specs.satisfyNone());
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

    @TaskAction
    public void doGenerate() {
        IvyModuleDescriptorInternal descriptorInternal = toIvyModuleDescriptorInternal(getDescriptor());

        IvyDescriptorFileGenerator ivyGenerator = new IvyDescriptorFileGenerator(descriptorInternal.getProjectIdentity());
        ivyGenerator.setStatus(descriptorInternal.getStatus());
        for (IvyConfiguration ivyConfiguration : descriptorInternal.getConfigurations()) {
            ivyGenerator.addConfiguration(ivyConfiguration);
        }

        for (IvyArtifact ivyArtifact : descriptorInternal.getArtifacts()) {
            ivyGenerator.addArtifact(ivyArtifact);
        }

        for (IvyDependencyInternal ivyDependency : descriptorInternal.getDependencies()) {
            ivyGenerator.addDependency(ivyDependency);
        }

        ivyGenerator.withXml(descriptorInternal.getXmlAction()).writeTo(getDestination());
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

}
