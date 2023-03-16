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

package org.gradle.language.plugins;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.PublishableComponent;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.transform.UnzipTransform;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.publisher.MutableMavenProjectIdentity;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.language.ComponentWithBinaries;
import org.gradle.language.ComponentWithOutputs;
import org.gradle.language.ComponentWithTargetMachines;
import org.gradle.language.ProductionComponent;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.nativeplatform.internal.ComponentWithNames;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithLinkUsage;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithRuntimeUsage;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithSharedLibrary;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithStaticLibrary;
import org.gradle.language.nativeplatform.internal.Names;
import org.gradle.language.nativeplatform.internal.PublicationAwareComponent;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.TargetMachine;
import org.gradle.nativeplatform.TargetMachineFactory;
import org.gradle.nativeplatform.internal.DefaultTargetMachineFactory;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.tasks.AbstractLinkTask;
import org.gradle.nativeplatform.tasks.CreateStaticLibrary;
import org.gradle.nativeplatform.tasks.ExtractSymbols;
import org.gradle.nativeplatform.tasks.InstallExecutable;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.tasks.StripSymbols;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.DIRECTORY_TYPE;
import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE;
import static org.gradle.language.cpp.CppBinary.LINKAGE_ATTRIBUTE;

/**
 * A common base plugin for the native plugins.
 *
 * <p>Expects plugins to register the native components in the {@link Project#getComponents()} container, and defines a number of rules that act on these components to configure them.</p>
 *
 * <ul>
 *
 * <li>Configures the {@value LifecycleBasePlugin#ASSEMBLE_TASK_NAME} task to build the development binary of the main component, if present. Expects the main component to be of type {@link ProductionComponent} and {@link ComponentWithBinaries}.</li>
 *
 * <li>Adds an {@code "assemble"} task for each binary of the main component.</li>
 *
 * <li>Adds tasks to compile and link an executable. Currently requires component implements internal API {@link ConfigurableComponentWithExecutable}.</li>
 *
 * <li>Adds tasks to compile and link a shared library. Currently requires component implements internal API {@link ConfigurableComponentWithSharedLibrary}.</li>
 *
 * <li>Adds tasks to compile and create a static library. Currently requires component implements internal API {@link ConfigurableComponentWithStaticLibrary}.</li>
 *
 * <li>Adds outgoing configuration and artifacts for link file. Currently requires component implements internal API {@link ConfigurableComponentWithLinkUsage}.</li>
 *
 * <li>Adds outgoing configuration and artifacts for runtime file. Currently requires component implements internal API {@link ConfigurableComponentWithRuntimeUsage}.</li>
 *
 * <li>Maven publications. Currently requires component implements internal API {@link PublicationAwareComponent}.</li>
 *
 * <li>Adds {@link TargetMachineFactory} for configuring {@link TargetMachine}.</li>
 *
 * </ul>
 *
 * @since 4.5
 */
@Incubating
public abstract class NativeBasePlugin implements Plugin<Project> {
    private final TargetMachineFactory targetMachineFactory;

    @Inject
    public NativeBasePlugin(TargetMachineFactory targetMachineFactory) {
        this.targetMachineFactory = targetMachineFactory;
    }

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);

        addTargetMachineFactoryAsExtension(project.getExtensions(), targetMachineFactory);

        final TaskContainer tasks = project.getTasks();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

        final SoftwareComponentContainer components = project.getComponents();

        addLifecycleTasks(project, tasks, components);

        // Add tasks to build various kinds of components

        addTasksForComponentWithExecutable(tasks, buildDirectory, components);
        addTasksForComponentWithSharedLibrary(tasks, buildDirectory, components);
        addTasksForComponentWithStaticLibrary(tasks, buildDirectory, components);

        // Add incoming artifact transforms
        final DependencyHandler dependencyHandler = project.getDependencies();
        final ObjectFactory objects = project.getObjects();

        addHeaderZipTransform(dependencyHandler, objects);

        // Add outgoing configurations and publications
        final RoleBasedConfigurationContainerInternal configurations = ((ProjectInternal) project).getConfigurations();

        project.getDependencies().getAttributesSchema().attribute(LINKAGE_ATTRIBUTE).getDisambiguationRules().add(LinkageSelectionRule.class);

        addOutgoingConfigurationForLinkUsage(components, configurations);
        addOutgoingConfigurationForRuntimeUsage(components, configurations);

        addPublicationsFromVariants(project, components);
    }

    private static void addTargetMachineFactoryAsExtension(ExtensionContainer extensions, TargetMachineFactory targetMachineFactory) {
        extensions.add(TargetMachineFactory.class, "machines", targetMachineFactory);
    }

    private void addLifecycleTasks(final Project project, final TaskContainer tasks, final SoftwareComponentContainer components) {
        components.withType(ComponentWithBinaries.class, component -> {
            // Register each child of each component
            component.getBinaries().whenElementKnown(binary -> components.add(binary));

            if (component instanceof ProductionComponent) {
                // Add an assemble task for each binary and also wire the development binary in to the `assemble` task
                component.getBinaries().whenElementFinalized(ComponentWithOutputs.class, binary -> {
                    // Determine which output to produce at development time.
                    final FileCollection outputs = binary.getOutputs();
                    Names names = ((ComponentWithNames) binary).getNames();
                    tasks.register(names.getTaskName("assemble"), task -> task.dependsOn(outputs));

                    if (binary == ((ProductionComponent) component).getDevelopmentBinary().get()) {
                        tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, task -> task.dependsOn(outputs));
                    }
                });
            }

            if (component instanceof ComponentWithTargetMachines) {
                ComponentWithTargetMachines componentWithTargetMachines = (ComponentWithTargetMachines)component;
                tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, task -> {
                    task.dependsOn((Callable) () -> {
                        TargetMachine currentHost = ((DefaultTargetMachineFactory)targetMachineFactory).host();
                        boolean targetsCurrentMachine = componentWithTargetMachines.getTargetMachines().get().stream().anyMatch(targetMachine -> currentHost.getOperatingSystemFamily().equals(targetMachine.getOperatingSystemFamily()));
                        if (!targetsCurrentMachine) {
                            task.getLogger().warn("'" + component.getName() + "' component in project '" + project.getPath() + "' does not target this operating system.");
                        }
                        return Collections.emptyList();
                    });
                });
            }
        });
    }

    private void addTasksForComponentWithExecutable(final TaskContainer tasks, final DirectoryProperty buildDirectory, SoftwareComponentContainer components) {
        components.withType(ConfigurableComponentWithExecutable.class, executable -> {
            final Names names = executable.getNames();
            final NativeToolChain toolChain = executable.getToolChain();
            final NativePlatform targetPlatform = executable.getNativePlatform();
            final PlatformToolProvider toolProvider = executable.getPlatformToolProvider();

            // Add a link task
            TaskProvider<LinkExecutable> link = tasks.register(names.getTaskName("link"), LinkExecutable.class, task -> {
                task.source(executable.getObjects());
                task.lib(executable.getLinkLibraries());
                task.getLinkedFile().set(buildDirectory.file(executable.getBaseName().map(baseName -> toolProvider.getExecutableName("exe/" + names.getDirName() + baseName))));
                task.getTargetPlatform().set(targetPlatform);
                task.getToolChain().set(toolChain);
                task.getDebuggable().set(executable.isDebuggable());
            });

            executable.getLinkTask().set(link);
            executable.getDebuggerExecutableFile().set(link.flatMap(linkExecutable -> linkExecutable.getLinkedFile()));

            if (executable.isDebuggable() && executable.isOptimized() && toolProvider.requiresDebugBinaryStripping()) {
                Provider<RegularFile> symbolLocation = buildDirectory.file(
                        executable.getBaseName().map(baseName -> toolProvider.getExecutableSymbolFileName("exe/" + names.getDirName() + "stripped/" + baseName)));
                Provider<RegularFile> strippedLocation = buildDirectory.file(
                        executable.getBaseName().map(baseName -> toolProvider.getExecutableName("exe/" + names.getDirName() + "stripped/" + baseName)));

                TaskProvider<StripSymbols> stripSymbols = stripSymbols(link, names, tasks, toolChain, targetPlatform, strippedLocation);
                executable.getExecutableFile().set(stripSymbols.flatMap(task -> task.getOutputFile()));
                TaskProvider<ExtractSymbols> extractSymbols = extractSymbols(link, names, tasks, toolChain, targetPlatform, symbolLocation);
                executable.getOutputs().from(extractSymbols.flatMap(task -> task.getSymbolFile()));
                executable.getExecutableFileProducer().set(stripSymbols);
            } else {
                executable.getExecutableFile().set(link.flatMap(task -> task.getLinkedFile()));
                executable.getExecutableFileProducer().set(link);
            }

            // Add an install task
            // TODO - should probably not add this for all executables?
            // TODO - add stripped symbols to the installation
            final TaskProvider<InstallExecutable> install = tasks.register(names.getTaskName("install"), InstallExecutable.class, task -> {
                task.getTargetPlatform().set(targetPlatform);
                task.getToolChain().set(toolChain);
                task.getInstallDirectory().set(buildDirectory.dir("install/" + names.getDirName()));
                task.getExecutableFile().set(executable.getExecutableFile());
                task.lib(executable.getRuntimeLibraries());
            });

            executable.getInstallTask().set(install);
            executable.getInstallDirectory().set(install.flatMap(task -> task.getInstallDirectory()));
            executable.getOutputs().from(executable.getInstallDirectory());

            executable.getDebuggerExecutableFile().set(install.flatMap(task -> task.getInstalledExecutable()));
        });
    }

    private void addTasksForComponentWithSharedLibrary(final TaskContainer tasks, final DirectoryProperty buildDirectory, SoftwareComponentContainer components) {
        components.withType(ConfigurableComponentWithSharedLibrary.class, library -> {
            final Names names = library.getNames();
            final NativePlatform targetPlatform = library.getNativePlatform();
            final NativeToolChain toolChain = library.getToolChain();
            final PlatformToolProvider toolProvider = library.getPlatformToolProvider();

            // Add a link task
            final TaskProvider<LinkSharedLibrary> link = tasks.register(names.getTaskName("link"), LinkSharedLibrary.class, task -> {
                task.source(library.getObjects());
                task.lib(library.getLinkLibraries());
                task.getLinkedFile().set(buildDirectory.file(library.getBaseName().map(baseName -> toolProvider.getSharedLibraryName("lib/" + names.getDirName() + baseName))));
                // TODO: We should set this for macOS, but this currently breaks XCTest support for Swift
                // when Swift depends on C++ libraries built by Gradle.
                if (!targetPlatform.getOperatingSystem().isMacOsX()) {
                    Provider<String> installName = task.getLinkedFile().getLocationOnly().map(linkedFile -> linkedFile.getAsFile().getName());
                    task.getInstallName().set(installName);
                }
                task.getTargetPlatform().set(targetPlatform);
                task.getToolChain().set(toolChain);
                task.getDebuggable().set(library.isDebuggable());
            });

            Provider<RegularFile> linkFile = link.flatMap(task -> task.getLinkedFile());
            Provider<RegularFile> runtimeFile = link.flatMap(task -> task.getLinkedFile());
            Provider<? extends Task> linkFileTask = link;

            if (toolProvider.producesImportLibrary()) {
                link.configure(linkSharedLibrary -> {
                    linkSharedLibrary.getImportLibrary().set(buildDirectory.file(
                            library.getBaseName().map(baseName -> toolProvider.getImportLibraryName("lib/" + names.getDirName() + baseName))));
                });
                linkFile = link.flatMap(task -> task.getImportLibrary());
            }

            if (library.isDebuggable() && library.isOptimized() && toolProvider.requiresDebugBinaryStripping()) {

                Provider<RegularFile> symbolLocation = buildDirectory.file(
                        library.getBaseName().map(baseName -> toolProvider.getLibrarySymbolFileName("lib/" + names.getDirName() + "stripped/" + baseName)));
                Provider<RegularFile> strippedLocation = buildDirectory.file(
                        library.getBaseName().map(baseName -> toolProvider.getSharedLibraryName("lib/" + names.getDirName() + "stripped/" + baseName)));

                TaskProvider<StripSymbols> stripSymbols = stripSymbols(link, names, tasks, toolChain, targetPlatform, strippedLocation);
                linkFile = runtimeFile = stripSymbols.flatMap(task -> task.getOutputFile());

                TaskProvider<ExtractSymbols> extractSymbols = extractSymbols(link, names, tasks, toolChain, targetPlatform, symbolLocation);
                library.getOutputs().from(extractSymbols.flatMap(task -> task.getSymbolFile()));
                linkFileTask = stripSymbols;
            }
            library.getLinkTask().set(link);
            library.getLinkFile().set(linkFile);
            library.getLinkFileProducer().set(linkFileTask);
            library.getRuntimeFile().set(runtimeFile);
            library.getOutputs().from(library.getLinkFile());
            library.getOutputs().from(library.getRuntimeFile());
        });
    }

    private void addTasksForComponentWithStaticLibrary(final TaskContainer tasks, final DirectoryProperty buildDirectory, SoftwareComponentContainer components) {
        components.withType(ConfigurableComponentWithStaticLibrary.class, library -> {
            final Names names = library.getNames();

            // Add a create task
            final TaskProvider<CreateStaticLibrary> createTask = tasks.register(names.getTaskName("create"), CreateStaticLibrary.class, task -> {
                task.source(library.getObjects());
                final PlatformToolProvider toolProvider = library.getPlatformToolProvider();
                Provider<RegularFile> linktimeFile = buildDirectory.file(
                        library.getBaseName().map(baseName -> toolProvider.getStaticLibraryName("lib/" + names.getDirName() + baseName)));
                task.getOutputFile().set(linktimeFile);
                task.getTargetPlatform().set(library.getNativePlatform());
                task.getToolChain().set(library.getToolChain());
            });

            // Wire the task into the library model
            library.getLinkFile().set(createTask.flatMap(task -> task.getBinaryFile()));
            library.getLinkFileProducer().set(createTask);
            library.getCreateTask().set(createTask);
            library.getOutputs().from(library.getLinkFile());
        });
    }

    private void addOutgoingConfigurationForLinkUsage(SoftwareComponentContainer components, final RoleBasedConfigurationContainerInternal configurations) {
        components.withType(ConfigurableComponentWithLinkUsage.class, component -> {
            Names names = component.getNames();

            @SuppressWarnings("deprecation") Configuration linkElements = configurations.createWithRole(names.withSuffix("linkElements"), ConfigurationRolesForMigration.INTENDED_CONSUMABLE_BUCKET_TO_INTENDED_CONSUMABLE);
            linkElements.extendsFrom(component.getImplementationDependencies());
            AttributeContainer attributes = component.getLinkAttributes();
            copyAttributesTo(attributes, linkElements);

            linkElements.getOutgoing().artifact(component.getLinkFile());

            component.getLinkElements().set(linkElements);
        });
    }

    private void addOutgoingConfigurationForRuntimeUsage(SoftwareComponentContainer components, final RoleBasedConfigurationContainerInternal configurations) {
        components.withType(ConfigurableComponentWithRuntimeUsage.class, component -> {
            Names names = component.getNames();

            @SuppressWarnings("deprecation") Configuration runtimeElements = configurations.createWithRole(names.withSuffix("runtimeElements"), ConfigurationRolesForMigration.INTENDED_CONSUMABLE_BUCKET_TO_INTENDED_CONSUMABLE);
            runtimeElements.extendsFrom(component.getImplementationDependencies());

            AttributeContainer attributes = component.getRuntimeAttributes();
            copyAttributesTo(attributes, runtimeElements);

            if (component.hasRuntimeFile()) {
                runtimeElements.getOutgoing().artifact(component.getRuntimeFile());
            }

            component.getRuntimeElements().set(runtimeElements);
        });
    }

    private void addPublicationsFromVariants(final Project project, final SoftwareComponentContainer components) {
        project.getPluginManager().withPlugin("maven-publish", plugin -> {
            components.withType(PublicationAwareComponent.class, component -> {
                project.getExtensions().configure(PublishingExtension.class, publishing -> {
                    final ComponentWithVariants mainVariant = component.getMainPublication();
                    publishing.getPublications().create("main", MavenPublication.class, publication -> {
                        MavenPublicationInternal publicationInternal = (MavenPublicationInternal) publication;
                        publicationInternal.getMavenProjectIdentity().getArtifactId().set(component.getBaseName());
                        publicationInternal.from(mainVariant);
                        publicationInternal.publishWithOriginalFileName();
                    });

                    Set<? extends SoftwareComponent> variants = mainVariant.getVariants();
                    if (variants instanceof DomainObjectSet) {
                        ((DomainObjectSet<? extends SoftwareComponent>) variants).all(child -> addPublicationFromVariant(child, publishing, project));
                    } else {
                        for (SoftwareComponent variant : variants) {
                            addPublicationFromVariant(variant, publishing, project);
                        }
                    }
                });
            });
        });
    }

    private void addPublicationFromVariant(SoftwareComponent child, PublishingExtension publishing, Project project) {
        if (child instanceof PublishableComponent) {
            publishing.getPublications().create(child.getName(), MavenPublication.class, publication -> {
                MavenPublicationInternal publicationInternal = (MavenPublicationInternal) publication;
                fillInCoordinates(project, publicationInternal, (PublishableComponent) child);
                publicationInternal.from(child);
                publicationInternal.publishWithOriginalFileName();
            });
        }
    }

    private void fillInCoordinates(Project project, MavenPublicationInternal publication, PublishableComponent publishableComponent) {
        final ModuleVersionIdentifier coordinates = publishableComponent.getCoordinates();
        MutableMavenProjectIdentity identity = publication.getMavenProjectIdentity();
        identity.getGroupId().set(project.provider(() -> coordinates.getGroup()));
        identity.getArtifactId().set(project.provider(() -> coordinates.getName()));
        identity.getVersion().set(project.provider(() -> coordinates.getVersion()));
    }

    private void copyAttributesTo(AttributeContainer attributes, Configuration linkElements) {
        for (Attribute<?> attribute : attributes.keySet()) {
            Object value = attributes.getAttribute(attribute);
            linkElements.getAttributes().attribute(Cast.<Attribute<Object>>uncheckedCast(attribute), value);
        }
    }

    private TaskProvider<StripSymbols> stripSymbols(final TaskProvider<? extends AbstractLinkTask> link, Names names, TaskContainer tasks, final NativeToolChain toolChain, final NativePlatform currentPlatform, final Provider<RegularFile> strippedLocation) {
        return tasks.register(names.getTaskName("stripSymbols"), StripSymbols.class, stripSymbols -> {
            stripSymbols.getBinaryFile().set(link.flatMap(task -> task.getLinkedFile()));
            stripSymbols.getOutputFile().set(strippedLocation);
            stripSymbols.getTargetPlatform().set(currentPlatform);
            stripSymbols.getToolChain().set(toolChain);
        });
    }

    private TaskProvider<ExtractSymbols> extractSymbols(final TaskProvider<? extends AbstractLinkTask> link, Names names, TaskContainer tasks, final NativeToolChain toolChain, final NativePlatform currentPlatform, final Provider<RegularFile> symbolLocation) {
        return tasks.register(names.getTaskName("extractSymbols"), ExtractSymbols.class, extractSymbols -> {
            extractSymbols.getBinaryFile().set(link.flatMap(task -> task.getLinkedFile()));
            extractSymbols.getSymbolFile().set(symbolLocation);
            extractSymbols.getTargetPlatform().set(currentPlatform);
            extractSymbols.getToolChain().set(toolChain);
        });
    }

    private void addHeaderZipTransform(DependencyHandler dependencyHandler, ObjectFactory objects) {
        dependencyHandler.registerTransform(UnzipTransform.class, variantTransform -> {
            variantTransform.getFrom().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ZIP_TYPE);
            variantTransform.getFrom().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.C_PLUS_PLUS_API));
            variantTransform.getTo().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, DIRECTORY_TYPE);
            variantTransform.getTo().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.C_PLUS_PLUS_API));
        });
    }

    static class LinkageSelectionRule implements AttributeDisambiguationRule<Linkage> {
        @Override
        public void execute(MultipleCandidatesDetails<Linkage> details) {
            if (details.getCandidateValues().contains(Linkage.SHARED)) {
                details.closestMatch(Linkage.SHARED);
            }
        }
    }
}
