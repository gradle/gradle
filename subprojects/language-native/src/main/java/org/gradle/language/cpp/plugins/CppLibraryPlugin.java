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

package org.gradle.language.cpp.plugins;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.Linkage;
import org.gradle.language.cpp.internal.DefaultCppLibrary;
import org.gradle.language.cpp.internal.MainLibraryVariant;
import org.gradle.language.cpp.internal.NativeVariant;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.LINKAGE_ATTRIBUTE;

/**
 * <p>A plugin that produces a native library from C++ source.</p>
 *
 * <p>Assumes the source files are located in `src/main/cpp`, public headers are located in `src/main/public` and implementation header files are located in `src/main/headers`.</p>
 *
 * <p>Adds a {@link CppLibrary} extension to the project to allow configuration of the library.</p>
 *
 * @since 4.1
 */
@Incubating
public class CppLibraryPlugin implements Plugin<ProjectInternal> {
    private final FileOperations fileOperations;

    /**
     * Injects a {@link FileOperations} instance.
     *
     * @since 4.2
     */
    @Inject
    public CppLibraryPlugin(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);

        final TaskContainer tasks = project.getTasks();
        ConfigurationContainer configurations = project.getConfigurations();
        ObjectFactory objectFactory = project.getObjects();
        ProviderFactory providers = project.getProviders();

        // Add the library extension
        final CppLibrary library = project.getExtensions().create(CppLibrary.class, "library", DefaultCppLibrary.class, "main", project.getLayout(), project.getObjects(), fileOperations, project.getConfigurations());
        project.getComponents().add(library);
        project.getComponents().add(library.getDebugSharedLibrary());
        project.getComponents().add(library.getReleaseSharedLibrary());
        project.getComponents().add(library.getDebugStaticLibrary());
        project.getComponents().add(library.getReleaseStaticLibrary());

        // Configure the component
        library.getBaseName().set(project.getName());

        // Define the outgoing artifacts
        // TODO - move this to the base plugin
        tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(library.getDevelopmentBinary().getLinkFile());

        // TODO - add lifecycle tasks
        // TODO - extract some common code to setup the configurations

        final Usage apiUsage = objectFactory.named(Usage.class, Usage.C_PLUS_PLUS_API);
        final Configuration apiElements = configurations.maybeCreate("cppApiElements");
        apiElements.extendsFrom(library.getApiDependencies());
        apiElements.setCanBeResolved(false);
        apiElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, apiUsage);
        // TODO - deal with more than one header dir, e.g. generated public headers
        Provider<File> publicHeaders = providers.provider(new Callable<File>() {
            @Override
            public File call() throws Exception {
                Set<File> files = library.getPublicHeaderDirs().getFiles();
                if (files.size() != 1) {
                    throw new UnsupportedOperationException(String.format("The C++ library plugin currently requires exactly one public header directory, however there are %d directories configured: %s", files.size(), files));
                }
                return files.iterator().next();
            }
        });
        apiElements.getOutgoing().artifact(publicHeaders);

        Configuration implementation = library.getImplementationDependencies();

        final Usage linkUsage = objectFactory.named(Usage.class, Usage.NATIVE_LINK);
        final Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);

        final Configuration debugLinkElements = maybeCreateAndConfigureDependencyElement(
            configurations, "debugLinkElements", implementation, linkUsage, true, Linkage.SHARED,
            library.getDebugSharedLibrary().getLinkFile());
        final Configuration debugRuntimeElements = maybeCreateAndConfigureDependencyElement(
            configurations, "debugRuntimeElements", implementation, runtimeUsage, true, Linkage.SHARED,
            library.getDebugSharedLibrary().getRuntimeFile());

        final Configuration releaseLinkElements = maybeCreateAndConfigureDependencyElement(
            configurations, "releaseLinkElements", implementation, linkUsage, false, Linkage.SHARED,
            library.getReleaseSharedLibrary().getLinkFile());
        final Configuration releaseRuntimeElements = maybeCreateAndConfigureDependencyElement(
            configurations, "releaseRuntimeElements", implementation, runtimeUsage, false, Linkage.SHARED,
            library.getReleaseSharedLibrary().getRuntimeFile());

        final Configuration debugStaticLinkElements = maybeCreateAndConfigureDependencyElement(
            configurations, "debugStaticLinkElements", implementation, linkUsage, true, Linkage.STATIC,
            library.getDebugStaticLibrary().getLinkFile());
        final Configuration debugStaticRuntimeElements = maybeCreateAndConfigureDependencyElement(
            configurations, "debugStaticRuntimeElements", implementation, runtimeUsage, true, Linkage.STATIC);

        final Configuration releaseStaticLinkElements = maybeCreateAndConfigureDependencyElement(
            configurations, "releaseStaticLinkElements", implementation, linkUsage, false, Linkage.STATIC,
            library.getReleaseStaticLibrary().getLinkFile());
        final Configuration releaseStaticRuntimeElements = maybeCreateAndConfigureDependencyElement(
            configurations, "releaseStaticRuntimeElements", implementation, runtimeUsage, false, Linkage.STATIC);

        project.getPluginManager().withPlugin("maven-publish", new Action<AppliedPlugin>() {
            @Override
            public void execute(AppliedPlugin appliedPlugin) {
                final Zip headersZip = tasks.create("cppHeaders", Zip.class);
                headersZip.from(library.getPublicHeaderFiles());
                // TODO - should track changes to build directory
                headersZip.setDestinationDir(new File(project.getBuildDir(), "headers"));
                headersZip.setClassifier("cpp-api-headers");
                headersZip.setArchiveName("cpp-api-headers.zip");

                final MainLibraryVariant mainVariant = new MainLibraryVariant("api", apiUsage, ImmutableSet.of(new ArchivePublishArtifact(headersZip)), apiElements);

                project.getExtensions().configure(PublishingExtension.class, new Action<PublishingExtension>() {
                    @Override
                    public void execute(PublishingExtension extension) {
                        extension.getPublications().create("main", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(library.getBaseName().get());
                                publication.setVersion(project.getVersion().toString());
                                // TODO - don't use internal types
                                publication.from(mainVariant);
                            }
                        });
                        extension.getPublications().create("debugShared", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(library.getBaseName().get() + "_debugShared");
                                publication.setVersion(project.getVersion().toString());
                                NativeVariant debugVariant = new NativeVariant("debugShared", linkUsage, debugLinkElements, runtimeUsage, debugRuntimeElements);
                                mainVariant.addVariant(debugVariant);
                                publication.from(debugVariant);
                            }
                        });
                        extension.getPublications().create("releaseShared", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(library.getBaseName().get() + "_releaseShared");
                                publication.setVersion(project.getVersion().toString());
                                NativeVariant releaseVariant = new NativeVariant("releaseShared", linkUsage, releaseLinkElements, runtimeUsage, releaseRuntimeElements);
                                mainVariant.addVariant(releaseVariant);
                                publication.from(releaseVariant);
                            }
                        });

                        extension.getPublications().create("debugStatic", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(library.getBaseName().get() + "_debugStatic");
                                publication.setVersion(project.getVersion().toString());
                                NativeVariant debugVariant = new NativeVariant("debugStatic", linkUsage, debugStaticLinkElements, runtimeUsage, debugStaticRuntimeElements);
                                mainVariant.addVariant(debugVariant);
                                publication.from(debugVariant);
                            }
                        });
                        extension.getPublications().create("releaseStatic", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(library.getBaseName().get() + "_releaseStatic");
                                publication.setVersion(project.getVersion().toString());
                                NativeVariant releaseVariant = new NativeVariant("releaseStatic", linkUsage, releaseStaticLinkElements, runtimeUsage, releaseStaticRuntimeElements);
                                mainVariant.addVariant(releaseVariant);
                                publication.from(releaseVariant);
                            }
                        });
                    }
                });
            }
        });
    }

    private Configuration maybeCreateAndConfigureDependencyElement(ConfigurationContainer configurations, String name, Configuration implementation, Usage usage, boolean debuggable, Linkage linkage, Provider<RegularFile> artifact) {
        Configuration elements = maybeCreateAndConfigureDependencyElement(configurations, name, implementation, usage, debuggable, linkage);
        elements.getOutgoing().artifact(artifact);

        return elements;
    }

    private Configuration maybeCreateAndConfigureDependencyElement(ConfigurationContainer configurations, String name, Configuration implementation, Usage usage, boolean debuggable, Linkage linkage) {
        Configuration elements = configurations.maybeCreate(name);
        elements.extendsFrom(implementation);
        elements.setCanBeResolved(false);
        elements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, usage);
        elements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debuggable);
        elements.getAttributes().attribute(LINKAGE_ATTRIBUTE, linkage);

        return elements;
    }
}
