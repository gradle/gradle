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
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.language.cpp.CppLibrary;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.cpp.internal.DefaultCppLibrary;
import org.gradle.language.cpp.internal.MainLibraryVariant;
import org.gradle.language.cpp.internal.NativeVariant;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.nativeplatform.Linkage;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;

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
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;

    /**
     * Injects a {@link FileOperations} instance.
     *
     * @since 4.2
     */
    @Inject
    public CppLibraryPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);

        final TaskContainer tasks = project.getTasks();
        final ConfigurationContainer configurations = project.getConfigurations();
        final ObjectFactory objectFactory = project.getObjects();
        final ProviderFactory providers = project.getProviders();

        // Add the library and extension
        final DefaultCppLibrary library = componentFactory.newInstance(CppLibrary.class, DefaultCppLibrary.class, "main");
        project.getExtensions().add(CppLibrary.class, "library", library);
        project.getComponents().add(library);

        // Configure the component
        library.getBaseName().set(project.getName());

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                library.getLinkage().lockNow();
                if (library.getLinkage().get().isEmpty()) {
                    throw new IllegalArgumentException("A linkage needs to be specified for the library.");
                }

                boolean sharedLibs = library.getLinkage().get().contains(Linkage.SHARED);
                boolean staticLibs = library.getLinkage().get().contains(Linkage.STATIC);

                ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class);

                final Usage apiUsage = objectFactory.named(Usage.class, Usage.C_PLUS_PLUS_API);
                final Usage linkUsage = objectFactory.named(Usage.class, Usage.NATIVE_LINK);
                final Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);

                Configuration implementation = library.getImplementationDependencies();

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

                // TODO - add lifecycle tasks
                // TODO - extract some common code to setup the configurations
                if (sharedLibs) {
                    String linkageNameSuffix = staticLibs ? "Shared" : "";
                    CppSharedLibrary debugSharedLibrary = library.addSharedLibrary("debug" + linkageNameSuffix, true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                    CppSharedLibrary releaseSharedLibrary = library.addSharedLibrary("release" + linkageNameSuffix, true, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                    // Use the debug shared library as the development binary
                    library.getDevelopmentBinary().set(debugSharedLibrary);

                    // Define the outgoing artifacts
                    // TODO - move this to the base plugin
                    final Configuration debugLinkElements = configurations.maybeCreate("debugLinkElements");
                    debugLinkElements.extendsFrom(implementation);
                    debugLinkElements.setCanBeResolved(false);
                    debugLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
                    debugLinkElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugSharedLibrary.isDebuggable());
                    debugLinkElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugSharedLibrary.isOptimized());
                    debugLinkElements.getOutgoing().artifact(debugSharedLibrary.getLinkFile());

                    final Configuration debugRuntimeElements = configurations.maybeCreate("debugRuntimeElements");
                    debugRuntimeElements.extendsFrom(implementation);
                    debugRuntimeElements.setCanBeResolved(false);
                    debugRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                    debugRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugSharedLibrary.isDebuggable());
                    debugRuntimeElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugSharedLibrary.isOptimized());
                    debugRuntimeElements.getOutgoing().artifact(debugSharedLibrary.getRuntimeFile());

                    final Configuration releaseLinkElements = configurations.maybeCreate("releaseLinkElements");
                    releaseLinkElements.extendsFrom(implementation);
                    releaseLinkElements.setCanBeResolved(false);
                    releaseLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
                    releaseLinkElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseSharedLibrary.isDebuggable());
                    releaseLinkElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseSharedLibrary.isOptimized());
                    releaseLinkElements.getOutgoing().artifact(releaseSharedLibrary.getLinkFile());

                    final Configuration releaseRuntimeElements = configurations.maybeCreate("releaseRuntimeElements");
                    releaseRuntimeElements.extendsFrom(implementation);
                    releaseRuntimeElements.setCanBeResolved(false);
                    releaseRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                    releaseRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseSharedLibrary.isDebuggable());
                    releaseRuntimeElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseSharedLibrary.isOptimized());
                    releaseRuntimeElements.getOutgoing().artifact(releaseSharedLibrary.getRuntimeFile());

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
                            NativeVariant debugVariant = new NativeVariant("debug", linkUsage, debugLinkElements, runtimeUsage, debugRuntimeElements);
                            mainVariant.addVariant(debugVariant);
                            NativeVariant releaseVariant = new NativeVariant("release", linkUsage, releaseLinkElements, runtimeUsage, releaseRuntimeElements);
                            mainVariant.addVariant(releaseVariant);

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
                                            publication.from(mainVariant);
                                            ((MavenPublicationInternal) publication).publishWithOriginalFileName();
                                        }
                                    });
                                    for (final SoftwareComponent child : mainVariant.getVariants()) {
                                        extension.getPublications().create(child.getName(), MavenPublication.class, new Action<MavenPublication>() {
                                            @Override
                                            public void execute(MavenPublication publication) {
                                                // TODO - should track changes to these properties
                                                publication.setGroupId(project.getGroup().toString());
                                                publication.setArtifactId(library.getBaseName().get() + "_" + child.getName());
                                                publication.setVersion(project.getVersion().toString());
                                                publication.from(child);
                                                ((MavenPublicationInternal) publication).publishWithOriginalFileName();
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    });
                }

                if (staticLibs) {
                    String linkageNameSuffix = sharedLibs ? "Static" : "";
                    CppStaticLibrary debugStaticLibrary = library.addStaticLibrary("debug" + linkageNameSuffix, true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                    CppStaticLibrary releaseStaticLibrary = library.addStaticLibrary("release" + linkageNameSuffix, true, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                    if (!sharedLibs) {
                        // Use the debug static library as the development binary
                        library.getDevelopmentBinary().set(debugStaticLibrary);

                        // Define the outgoing artifacts
                        // TODO - move this to the base plugin
                        final Configuration debugLinkElements = configurations.maybeCreate("debugStaticLinkElements");
                        debugLinkElements.extendsFrom(implementation);
                        debugLinkElements.setCanBeResolved(false);
                        debugLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
                        debugLinkElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugStaticLibrary.isDebuggable());
                        debugLinkElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugStaticLibrary.isOptimized());
                        debugLinkElements.getOutgoing().artifact(debugStaticLibrary.getLinkFile());

                        final Configuration debugRuntimeElements = configurations.maybeCreate("debugStaticRuntimeElements");
                        debugRuntimeElements.extendsFrom(implementation);
                        debugRuntimeElements.setCanBeResolved(false);
                        debugRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                        debugRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugStaticLibrary.isDebuggable());
                        debugRuntimeElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugStaticLibrary.isOptimized());

                        final Configuration releaseLinkElements = configurations.maybeCreate("releaseStaticLinkElements");
                        releaseLinkElements.extendsFrom(implementation);
                        releaseLinkElements.setCanBeResolved(false);
                        releaseLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
                        releaseLinkElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseStaticLibrary.isDebuggable());
                        releaseLinkElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseStaticLibrary.isOptimized());
                        releaseLinkElements.getOutgoing().artifact(releaseStaticLibrary.getLinkFile());

                        final Configuration releaseRuntimeElements = configurations.maybeCreate("releaseStaticRuntimeElements");
                        releaseRuntimeElements.extendsFrom(implementation);
                        releaseRuntimeElements.setCanBeResolved(false);
                        releaseRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                        releaseRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseStaticLibrary.isDebuggable());
                        releaseRuntimeElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseStaticLibrary.isOptimized());
                    }
                }

                library.getBinaries().realizeNow();
            }
        });
    }
}
