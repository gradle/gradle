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
import org.gradle.api.Action;
import org.gradle.api.Buildable;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.internal.metadata.GradleModuleMetadataWriter;
import org.gradle.api.publish.internal.metadata.ModuleMetadataSpec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Cast;
import org.gradle.internal.Try;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.serialization.Cached;
import org.gradle.internal.serialization.Transient;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

/**
 * Generates a Gradle metadata file to represent a published {@link org.gradle.api.component.SoftwareComponent} instance.
 *
 * @since 4.3
 */
public class GenerateModuleMetadata extends DefaultTask {
    private final Transient<Property<Publication>> publication;
    private final Transient<ListProperty<Publication>> publications;
    private final RegularFileProperty outputFile;
    private final FileCollection variantFiles;
    private final Cached<InputState> inputState = Cached.of(this::computeInputState);

    public GenerateModuleMetadata() {
        ObjectFactory objectFactory = getProject().getObjects();
        publication = Transient.of(objectFactory.property(Publication.class));
        publications = Transient.of(objectFactory.listProperty(Publication.class));

        outputFile = objectFactory.fileProperty();

        variantFiles = getFileCollectionFactory().create(new VariantFiles());

        // TODO - should be incremental
        getOutputs().upToDateWhen(Specs.satisfyNone());
        setOnlyIf(spec(task -> hasAttachedComponent()));
    }

    // TODO - this should be an input

    /**
     * Returns the publication to generate the metadata file for.
     */
    @Internal
    public Property<Publication> getPublication() {
        return publication.get();
    }

    // TODO - this should be an input

    /**
     * Returns the publications of the current project, used in generation to connect the modules of a component together.
     *
     * @since 4.4
     */
    @Internal
    public ListProperty<Publication> getPublications() {
        return publications.get();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    FileCollection getArtifacts() {
        return variantFiles;
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
     * Returns the {@link ChecksumService} to use.
     *
     * @since 6.6
     */
    @Inject
    protected ChecksumService getChecksumService() {
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
        InputState inputState = inputState();
        if (!(inputState instanceof InputState.Ready)) {
            throw new IllegalStateException(inputState.toString());
        }
        writeModuleMetadata(
            ((InputState.Ready) inputState).moduleMetadataSpec.get()
        );
    }

    private void writeModuleMetadata(ModuleMetadataSpec moduleMetadataSpec) {
        try (Writer writer = bufferedWriterFor(outputFile.get().getAsFile())) {
            moduleMetadataWriter().writeTo(writer, moduleMetadataSpec);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not generate metadata file " + outputFile.get(), e);
        }
    }

    private BufferedWriter bufferedWriterFor(File file) throws FileNotFoundException {
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), UTF_8));
    }

    private GradleModuleMetadataWriter moduleMetadataWriter() {
        return new GradleModuleMetadataWriter(
            getBuildInvocationScopeId(),
            getProjectDependencyPublicationResolver(),
            getChecksumService()
        );
    }

    private boolean hasAttachedComponent() {
        InputState inputState = inputState();
        if (inputState instanceof InputState.ComponentMissing) {
            String publicationName = ((InputState.ComponentMissing) inputState).publicationName;
            getLogger().warn(
                publicationName + " isn't attached to a component. Gradle metadata only supports publications with software components (e.g. from component.java)"
            );
            return false;
        }
        return true;
    }

    private InputState computeInputState() {
        return component() == null
            ? new InputState.ComponentMissing(publicationName())
            : new InputState.Ready(moduleMetadataSpec());
    }

    private Try<ModuleMetadataSpec> moduleMetadataSpec() {
        return Try.ofFailable(this::computeModuleMetadataSpec);
    }

    private ModuleMetadataSpec computeModuleMetadataSpec() {
        return moduleMetadataWriter().moduleMetadataSpecFor(publication(), publications());
    }

    static class InputState {

        static class Ready extends InputState {
            final Try<ModuleMetadataSpec> moduleMetadataSpec;

            public Ready(Try<ModuleMetadataSpec> moduleMetadataSpec) {
                this.moduleMetadataSpec = moduleMetadataSpec;
            }
        }

        static class ComponentMissing extends InputState {
            final String publicationName;

            public ComponentMissing(String publicationName) {
                this.publicationName = publicationName;
            }
        }
    }

    private class VariantFiles implements MinimalFileSet, Buildable {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "files of " + GenerateModuleMetadata.this.getPath();
        }

        @Override
        @Nonnull
        public TaskDependency getBuildDependencies() {
            DefaultTaskDependency dependency = new DefaultTaskDependency();
            SoftwareComponentInternal component = component();
            if (component != null) {
                forEachArtifactOf(component, dependency::add);
            }
            return dependency;
        }

        @Override
        @Nonnull
        public Set<File> getFiles() {
            SoftwareComponentInternal component = component();
            return component == null ? ImmutableSet.of() : filesOf(component);
        }

        private Set<File> filesOf(SoftwareComponentInternal component) {
            Set<File> files = new LinkedHashSet<>();
            forEachArtifactOf(component, artifact -> files.add(artifact.getFile()));
            return files;

        }

        private void forEachArtifactOf(SoftwareComponentInternal component, Action<PublishArtifact> action) {
            for (UsageContext usageContext : component.getUsages()) {
                for (PublishArtifact publishArtifact : usageContext.getArtifacts()) {
                    action.execute(publishArtifact);
                }
            }
        }
    }

    private InputState inputState() {
        return this.inputState.get();
    }

    private String publicationName() {
        return publication().getDisplayName().toString();
    }

    private SoftwareComponentInternal component() {
        return publication().getComponent();
    }

    private PublicationInternal<?> publication() {
        return Cast.uncheckedNonnullCast(publication.get().get());
    }

    private List<PublicationInternal<?>> publications() {
        return Cast.uncheckedCast(publications.get().get());
    }
}
