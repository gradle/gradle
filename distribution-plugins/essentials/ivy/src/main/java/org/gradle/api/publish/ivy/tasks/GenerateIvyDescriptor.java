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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyModuleDescriptorAuthor;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.IvyModuleDescriptorLicense;
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec;
import org.gradle.api.publish.ivy.internal.dependency.IvyDependencyInternal;
import org.gradle.api.publish.ivy.internal.dependency.IvyExcludeRule;
import org.gradle.api.publish.ivy.internal.publication.IvyModuleDescriptorSpecInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.file.PathToFileResolver;

import javax.inject.Inject;
import java.io.File;

/**
 * Generates an Ivy XML Module Descriptor file.
 *
 * @since 1.4
 */
public class GenerateIvyDescriptor extends DefaultTask {

    private IvyModuleDescriptorSpec descriptor;
    private Object destination;

    public GenerateIvyDescriptor() {
        // Never up to date; we don't understand the data structures.
        getOutputs().upToDateWhen(Specs.satisfyNone());
    }

    @Inject
    protected PathToFileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    /**
     * The module descriptor metadata.
     *
     * @return The module descriptor.
     */
    @Internal
    public IvyModuleDescriptorSpec getDescriptor() {
        return descriptor;
    }

    public void setDescriptor(IvyModuleDescriptorSpec descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * The file the descriptor will be written to.
     *
     * @return The file the descriptor will be written to
     */
    @OutputFile
    public File getDestination() {
        return destination == null ? null : getFileResolver().resolve(destination);
    }

    /**
     * Sets the destination the descriptor will be written to.
     *
     * @param destination The file the descriptor will be written to.
     * @since 4.0
     */
    public void setDestination(File destination) {
        this.destination = destination;
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
        IvyModuleDescriptorSpecInternal descriptorInternal = toIvyModuleDescriptorInternal(getDescriptor());

        IvyDescriptorFileGenerator ivyGenerator = new IvyDescriptorFileGenerator(descriptorInternal.getProjectIdentity(),
                                                                                 descriptorInternal.writeGradleMetadataMarker(),
                                                                                 descriptorInternal.getVersionMappingStrategy());
        ivyGenerator.setStatus(descriptorInternal.getStatus());
        ivyGenerator.setBranch(descriptorInternal.getBranch());
        ivyGenerator.setExtraInfo(descriptorInternal.getExtraInfo().asMap());

        for (IvyModuleDescriptorAuthor ivyAuthor : descriptorInternal.getAuthors()) {
            ivyGenerator.addAuthor(ivyAuthor);
        }

        for (IvyModuleDescriptorLicense ivyLicense : descriptorInternal.getLicenses()) {
            ivyGenerator.addLicense(ivyLicense);
        }

        ivyGenerator.setDescription(descriptorInternal.getDescription());

        for (IvyConfiguration ivyConfiguration : descriptorInternal.getConfigurations()) {
            ivyGenerator.addConfiguration(ivyConfiguration);
        }

        for (IvyArtifact ivyArtifact : descriptorInternal.getArtifacts()) {
            ivyGenerator.addArtifact(ivyArtifact);
        }

        for (IvyDependencyInternal ivyDependency : descriptorInternal.getDependencies()) {
            ivyGenerator.addDependency(ivyDependency);
        }

        for (IvyExcludeRule excludeRule : descriptorInternal.getGlobalExcludes()) {
            ivyGenerator.addGlobalExclude(excludeRule);
        }

        ivyGenerator.withXml(descriptorInternal.getXmlAction()).writeTo(getDestination());
    }

    private static IvyModuleDescriptorSpecInternal toIvyModuleDescriptorInternal(IvyModuleDescriptorSpec ivyModuleDescriptorSpec) {
        if (ivyModuleDescriptorSpec == null) {
            return null;
        } else if (ivyModuleDescriptorSpec instanceof IvyModuleDescriptorSpecInternal) {
            return (IvyModuleDescriptorSpecInternal) ivyModuleDescriptorSpec;
        } else {
            throw new InvalidUserDataException(
                    String.format(
                            "ivyModuleDescriptor implementations must implement the '%s' interface, implementation '%s' does not",
                            IvyModuleDescriptorSpecInternal.class.getName(),
                            ivyModuleDescriptorSpec.getClass().getName()
                    )
            );
        }
    }

}
