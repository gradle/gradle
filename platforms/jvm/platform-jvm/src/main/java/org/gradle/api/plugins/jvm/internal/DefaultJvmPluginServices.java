/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.Directory;
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal;
import org.gradle.api.internal.artifacts.publish.AbstractProviderBackedPublishArtifact;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.BuildableBackedProvider;
import org.gradle.api.internal.tasks.DefaultSourceSetOutput;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstanceGenerator;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Date;
import java.util.stream.Collectors;

public class DefaultJvmPluginServices implements JvmPluginServices {
    private final ObjectFactory objectFactory;
    private final InstanceGenerator instanceGenerator;
    private final ProjectInternal project;
    private final FileFactory fileFactory;

    @Inject
    public DefaultJvmPluginServices(
        ObjectFactory objectFactory,
        InstanceGenerator instanceGenerator,
        ProjectInternal project,
        FileFactory fileFactory
    ) {
        this.objectFactory = objectFactory;
        this.instanceGenerator = instanceGenerator;
        this.project = project;
        this.fileFactory = fileFactory;
    }

    @Override
    public void configureAsCompileClasspath(HasConfigurableAttributes<?> configuration) {
        configureAttributes(
            configuration,
            details -> details.library().apiUsage().withExternalDependencies().preferStandardJVM()
        );
    }

    @Override
    public void configureAsRuntimeClasspath(HasConfigurableAttributes<?> configuration) {
        configureAttributes(
            configuration,
            details -> details.library().runtimeUsage().asJar().withExternalDependencies().preferStandardJVM()
        );
    }

    @Override
    public void configureAsSources(HasConfigurableAttributes<?> configuration) {
        configureAttributes(
            configuration,
            details -> details.withExternalDependencies().asSources()
        );
    }

    @Override
    public void configureAsApiElements(HasConfigurableAttributes<?> configuration) {
        configureAttributes(
            configuration,
            details -> details.library().apiUsage().asJar().withExternalDependencies()
        );
    }

    @Override
    public void configureAsRuntimeElements(HasConfigurableAttributes<?> configuration) {
        configureAttributes(
            configuration,
            details -> details.library().runtimeUsage().asJar().withExternalDependencies()
        );
    }

    @Override
    public <T> void configureAttributes(HasConfigurableAttributes<T> configurable, Action<? super JvmEcosystemAttributesDetails> configuration) {
        AttributeContainerInternal attributes = (AttributeContainerInternal) configurable.getAttributes();
        DefaultJvmEcosystemAttributesDetails details = instanceGenerator.newInstance(DefaultJvmEcosystemAttributesDetails.class, objectFactory, attributes);
        configuration.execute(details);
    }

    @Override
    public void replaceArtifacts(Configuration outgoingConfiguration, Object... providers) {
        clearArtifacts(outgoingConfiguration);
        ConfigurationPublications outgoing = outgoingConfiguration.getOutgoing();
        for (Object provider : providers) {
            outgoing.artifact(provider);
        }
    }

    private void clearArtifacts(Configuration outgoingConfiguration) {
        outgoingConfiguration.getOutgoing().getArtifacts().clear();
        for (Configuration configuration : outgoingConfiguration.getExtendsFrom()) {
            clearArtifacts(configuration);
        }
    }

    @Override
    public ConfigurationVariant configureResourcesDirectoryVariant(Configuration configuration, SourceSet sourceSet) {
        ConfigurationPublications publications = configuration.getOutgoing();
        ConfigurationVariantInternal variant = (ConfigurationVariantInternal) publications.getVariants().maybeCreate("resources");
        variant.setDescription("Directories containing assembled resource files for " + sourceSet.getName() + ".");
        variant.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.RESOURCES));
        DefaultSourceSetOutput output = Cast.uncheckedCast(sourceSet.getOutput());
        Provider<File> resourcesContribution = output.getResourcesContribution();
        if (resourcesContribution != null) {
            Provider<Directory> dirProvider = resourcesContribution.map(fileFactory::dir);
            variant.artifact(new LazyJavaDirectoryArtifact(project.getTaskDependencyFactory(), ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY, dirProvider));
        }
        return variant;
    }

    @Override
    public ConfigurationVariant configureClassesDirectoryVariant(Configuration configuration, SourceSet sourceSet) {
        ConfigurationPublications publications = configuration.getOutgoing();
        ConfigurationVariantInternal variant = (ConfigurationVariantInternal) publications.getVariants().maybeCreate("classes");
        variant.setDescription("Directories containing compiled class files for " + sourceSet.getName() + ".");
        variant.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objectFactory.named(LibraryElements.class, LibraryElements.CLASSES));
        variant.artifactsProvider(() ->  {
            // TODO: We should not eagerly iterate over the output files. The contents of the source set output
            // are not necessarily known util after task execution. We should use sourceSet.getOutput().getClassesDirs().getElements()
            // in some manner. However, at the moment, it is difficult/not possible to create a Set<PublishArtifact> from
            // Provider<Set<FileSystemLocation>>
            FileCollectionInternal classesDirs = (FileCollectionInternal) sourceSet.getOutput().getClassesDirs();
            return classesDirs.getFiles().stream()
                .map(file -> {
                    Provider<Directory> dirProvider = new BuildableBackedProvider<>(classesDirs, Directory.class, () -> fileFactory.dir(file));
                    return new LazyJavaDirectoryArtifact(project.getTaskDependencyFactory(), ArtifactTypeDefinition.JVM_CLASS_DIRECTORY, dirProvider);
                })
                .collect(Collectors.toList());
        });
        return variant;
    }

    /**
     * A non-configurable publish artifact representing a directory.
     */
    private static class LazyJavaDirectoryArtifact extends AbstractProviderBackedPublishArtifact {

        private final String type;

        public LazyJavaDirectoryArtifact(TaskDependencyFactory taskDependencyFactory, String type, Provider<Directory> fileProvider) {
            super(taskDependencyFactory, fileProvider);
            this.type = type;
        }

        @Override
        public String getName() {
            return getFile().getName();
        }

        @Override
        public String getExtension() {
            return "";
        }

        @Override
        public String getType() {
            return type;
        }

        @Nullable
        @Override
        public String getClassifier() {
            return null;
        }

        @Override
        public Date getDate() {
            return null;
        }

        @Override
        public boolean shouldBePublished() {
            return false;
        }

    }
}
