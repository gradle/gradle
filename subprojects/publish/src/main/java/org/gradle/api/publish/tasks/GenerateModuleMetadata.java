/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.publish.tasks;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Buildable;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.internal.ModuleMetadataFileGenerator;
import org.gradle.api.publish.internal.ProjectDependencyPublicationResolver;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Cast;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a Gradle metadata file to represent a published {@link org.gradle.api.component.SoftwareComponent} instance.
 *
 * @since 4.3
 */
@Incubating
public class GenerateModuleMetadata extends DefaultTask {
    private final Property<Publication> publication;
    private final ListProperty<Publication> publications;
    private final RegularFileProperty outputFile;

    public GenerateModuleMetadata() {
        publication = getProject().getObjects().property(Publication.class);
        publications = getProject().getObjects().listProperty(Publication.class);
        outputFile = newOutputFile();
        // TODO - should be incremental
        getOutputs().upToDateWhen(Specs.<Task>satisfyNone());
    }

    // TODO - this should be an input
    /**
     * Returns the publication to generate the metadata file for.
     */
    @Internal
    public Property<Publication> getPublication() {
        return publication;
    }

    // TODO - this should be an input
    /**
     * Returns the publications of the current project, used in generation to connect the modules of a component together.
     *
     * @since 4.4
     */
    @Internal
    public ListProperty<Publication> getPublications() {
        return publications;
    }

    @InputFiles
    FileCollection getArtifacts() {
        return getFileCollectionFactory().create(new VariantFiles());
    }

    /**
     * Returns the {@link FileCollectionFactory} to use for generation.
     *
     * @since 4.4
     */
    @Inject
    protected FileCollectionFactory getFileCollectionFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the {@link BuildInvocationScopeId} to use for generation.
     *
     * @since 4.4
     */
    @Inject
    protected BuildInvocationScopeId getBuildInvocationScopeId() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the {@link ProjectDependencyPublicationResolver} to use for generation.
     *
     * @since 4.4
     */
    @Inject
    protected ProjectDependencyPublicationResolver getProjectDependencyPublicationResolver() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the output file location.
     */
    @OutputFile
    public RegularFileProperty getOutputFile() {
        return outputFile;
    }

    @TaskAction
    void run() {
        File file = outputFile.get().getAsFile();
        PublicationInternal publication = (PublicationInternal) this.publication.get();
        List<PublicationInternal> publications = Cast.uncheckedCast(this.publications.get());
        try {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "utf8"));
            try {
                new ModuleMetadataFileGenerator(getBuildInvocationScopeId(), getProjectDependencyPublicationResolver()).generateTo(publication, publications, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not generate metadata file " + outputFile.get(), e);
        }
    }

    private class VariantFiles implements MinimalFileSet, Buildable {
        @Override
        public TaskDependency getBuildDependencies() {
            PublicationInternal publication = (PublicationInternal) GenerateModuleMetadata.this.publication.get();
            SoftwareComponentInternal component = publication.getComponent();
            DefaultTaskDependency dependency = new DefaultTaskDependency();
            if (component == null) {
                return dependency;
            }
            for (UsageContext usageContext : component.getUsages()) {
                for (PublishArtifact publishArtifact : usageContext.getArtifacts()) {
                    dependency.add(publishArtifact);
                }
            }
            return dependency;
        }

        @Override
        public Set<File> getFiles() {
            PublicationInternal publication = (PublicationInternal) GenerateModuleMetadata.this.publication.get();
            SoftwareComponentInternal component = publication.getComponent();
            if (component == null) {
                return ImmutableSet.of();
            }
            Set<File> files = new LinkedHashSet<File>();
            for (UsageContext usageContext : component.getUsages()) {
                for (PublishArtifact publishArtifact : usageContext.getArtifacts()) {
                    files.add(publishArtifact.getFile());
                }
            }
            return files;
        }

        @Override
        public String getDisplayName() {
            return "files of " + GenerateModuleMetadata.this.getPath();
        }
    }
}
