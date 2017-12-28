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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
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

                final Usage linkUsage = objectFactory.named(Usage.class, Usage.NATIVE_LINK);
                final Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);

                final MainLibraryVariant mainVariant = library.getMainPublication();

                final Configuration apiElements = library.getApiElements();
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

                project.getPluginManager().withPlugin("maven-publish", new Action<AppliedPlugin>() {
                    @Override
                    public void execute(AppliedPlugin appliedPlugin) {
                        final Zip headersZip = tasks.create("cppHeaders", Zip.class);
                        headersZip.from(library.getPublicHeaderFiles());
                        // TODO - should track changes to build directory
                        headersZip.setDestinationDir(new File(project.getBuildDir(), "headers"));
                        headersZip.setClassifier("cpp-api-headers");
                        headersZip.setArchiveName("cpp-api-headers.zip");
                        mainVariant.addArtifact(new ArchivePublishArtifact(headersZip));
                    }
                });

                if (sharedLibs) {
                    String linkageNameSuffix = staticLibs ? "Shared" : "";
                    CppSharedLibrary debugSharedLibrary = library.addSharedLibrary("debug" + linkageNameSuffix, true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                    CppSharedLibrary releaseSharedLibrary = library.addSharedLibrary("release" + linkageNameSuffix, true, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                    // Use the debug shared library as the development binary
                    library.getDevelopmentBinary().set(debugSharedLibrary);

                    // Define the outgoing artifacts
                    final Configuration debugLinkElements = debugSharedLibrary.getLinkElements().get();
                    final Configuration debugRuntimeElements = debugSharedLibrary.getRuntimeElements().get();
                    final Configuration releaseLinkElements = releaseSharedLibrary.getLinkElements().get();
                    final Configuration releaseRuntimeElements = releaseSharedLibrary.getRuntimeElements().get();

                    NativeVariant debugVariant = new NativeVariant("debug" + linkageNameSuffix, linkUsage, debugLinkElements, runtimeUsage, debugRuntimeElements);
                    mainVariant.addVariant(debugVariant);
                    NativeVariant releaseVariant = new NativeVariant("release" + linkageNameSuffix, linkUsage, releaseLinkElements, runtimeUsage, releaseRuntimeElements);
                    mainVariant.addVariant(releaseVariant);
                }

                if (staticLibs) {
                    String linkageNameSuffix = sharedLibs ? "Static" : "";
                    CppStaticLibrary debugStaticLibrary = library.addStaticLibrary("debug" + linkageNameSuffix, true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                    CppStaticLibrary releaseStaticLibrary = library.addStaticLibrary("release" + linkageNameSuffix, true, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                    if (!sharedLibs) {
                        // Use the debug static library as the development binary
                        library.getDevelopmentBinary().set(debugStaticLibrary);
                    }

                    // Define the outgoing artifacts
                    final Configuration debugLinkElements = debugStaticLibrary.getLinkElements().get();
                    final Configuration debugRuntimeElements = debugStaticLibrary.getRuntimeElements().get();
                    final Configuration releaseLinkElements = releaseStaticLibrary.getLinkElements().get();
                    final Configuration releaseRuntimeElements = releaseStaticLibrary.getRuntimeElements().get();

                    NativeVariant debugVariant = new NativeVariant("debug" + linkageNameSuffix, linkUsage, debugLinkElements, runtimeUsage, debugRuntimeElements);
                    mainVariant.addVariant(debugVariant);
                    NativeVariant releaseVariant = new NativeVariant("release" + linkageNameSuffix, linkUsage, releaseLinkElements, runtimeUsage, releaseRuntimeElements);
                    mainVariant.addVariant(releaseVariant);
                }

                library.getBinaries().realizeNow();
            }
        });
    }
}
