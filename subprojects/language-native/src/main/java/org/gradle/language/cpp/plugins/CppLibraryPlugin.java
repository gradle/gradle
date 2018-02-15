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

import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.component.UsageContext;
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
import org.gradle.language.cpp.internal.LightweightUsageContext;
import org.gradle.language.cpp.internal.MainLibraryVariant;
import org.gradle.language.cpp.internal.NativeVariantIdentity;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;
import org.gradle.nativeplatform.Linkage;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.language.cpp.CppBinary.*;

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
    private final ImmutableAttributesFactory attributesFactory;

    /**
     * Injects a {@link FileOperations} instance.
     *
     * @since 4.2
     */
    @Inject
    public CppLibraryPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector, ImmutableAttributesFactory attributesFactory) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
        this.attributesFactory = attributesFactory;
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
                library.getOperatingSystems().lockNow();
                if (library.getOperatingSystems().get().isEmpty()) {
                    throw new IllegalArgumentException("An operating system needs to be specified for the application.");
                }

                library.getLinkage().lockNow();
                if (library.getLinkage().get().isEmpty()) {
                    throw new IllegalArgumentException("A linkage needs to be specified for the library.");
                }

                Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);
                Usage linkUsage = objectFactory.named(Usage.class, Usage.NATIVE_LINK);
                for (OperatingSystemFamily operatingSystem : library.getOperatingSystems().get()) {
                    String operatingSystemSuffix = "";
                    if (library.getOperatingSystems().get().size() > 1) {
                        operatingSystemSuffix = StringUtils.capitalize(operatingSystem.getName());
                    }

                    for (Linkage linkage : library.getLinkage().get()) {
                        String linkageSuffix = "";
                        if (library.getLinkage().get().size() > 1) {
                            linkageSuffix = StringUtils.capitalize(linkage.name().toLowerCase());
                        }

                        Provider<String> group = project.provider(new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                return project.getGroup().toString();
                            }
                        });

                        Provider<String> version = project.provider(new Callable<String>() {
                            @Override
                            public String call() throws Exception {
                                return project.getVersion().toString();
                            }
                        });

                        AttributeContainer attributesDebugRuntime = attributesFactory.mutable();
                        attributesDebugRuntime.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                        attributesDebugRuntime.attribute(DEBUGGABLE_ATTRIBUTE, true);
                        attributesDebugRuntime.attribute(OPTIMIZED_ATTRIBUTE, false);
                        attributesDebugRuntime.attribute(LINKAGE_ATTRIBUTE, linkage);
                        attributesDebugRuntime.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, operatingSystem);

                        AttributeContainer attributesDebugLink = attributesFactory.mutable();
                        attributesDebugLink.attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
                        attributesDebugLink.attribute(DEBUGGABLE_ATTRIBUTE, true);
                        attributesDebugLink.attribute(OPTIMIZED_ATTRIBUTE, false);
                        attributesDebugLink.attribute(LINKAGE_ATTRIBUTE, linkage);
                        attributesDebugLink.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, operatingSystem);

                        Set<? extends UsageContext> usageContextsDebug = Sets.newHashSet(new LightweightUsageContext("debug" + linkageSuffix + operatingSystemSuffix + "-runtime", runtimeUsage, attributesDebugRuntime), new LightweightUsageContext("debug" + linkageSuffix + operatingSystemSuffix + "-link", linkUsage, attributesDebugLink));

                        NativeVariantIdentity debugVariant = new NativeVariantIdentity("debug" + linkageSuffix + operatingSystemSuffix, library.getBaseName(), group, version, usageContextsDebug);
                        library.getMainPublication().addVariant(debugVariant);


                        AttributeContainer attributesReleaseRuntime = attributesFactory.mutable();
                        attributesReleaseRuntime.attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                        attributesReleaseRuntime.attribute(DEBUGGABLE_ATTRIBUTE, true);
                        attributesReleaseRuntime.attribute(OPTIMIZED_ATTRIBUTE, true);
                        attributesReleaseRuntime.attribute(LINKAGE_ATTRIBUTE, linkage);
                        attributesReleaseRuntime.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, operatingSystem);

                        AttributeContainer attributesReleaseLink = attributesFactory.mutable();
                        attributesReleaseLink.attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
                        attributesReleaseLink.attribute(DEBUGGABLE_ATTRIBUTE, true);
                        attributesReleaseLink.attribute(OPTIMIZED_ATTRIBUTE, true);
                        attributesReleaseLink.attribute(LINKAGE_ATTRIBUTE, linkage);
                        attributesReleaseLink.attribute(OperatingSystemFamily.OPERATING_SYSTEM_ATTRIBUTE, operatingSystem);

                        Set<? extends UsageContext> usageContextsRelease = Sets.newHashSet(new LightweightUsageContext("release" + linkageSuffix + operatingSystemSuffix + "-runtime", runtimeUsage, attributesReleaseRuntime), new LightweightUsageContext("release" + linkageSuffix + operatingSystemSuffix + "-link", linkUsage, attributesReleaseLink));


                        NativeVariantIdentity releaseVariant = new NativeVariantIdentity("release" + linkageSuffix + operatingSystemSuffix, library.getBaseName(), group, version, usageContextsRelease);
                        library.getMainPublication().addVariant(releaseVariant);


                        if (DefaultNativePlatform.getCurrentOperatingSystem().toFamilyName().equals(operatingSystem.getName())) {
                            ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class);

                            if (linkage == Linkage.SHARED) {
                                CppSharedLibrary debugSharedLibrary = library.addSharedLibrary("debug" + linkageSuffix + operatingSystemSuffix, true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider(), debugVariant);
                                library.addSharedLibrary("release" + linkageSuffix + operatingSystemSuffix, true, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider(), releaseVariant);

                                // Use the debug shared library as the development binary
                                library.getDevelopmentBinary().set(debugSharedLibrary);
                            } else {
                                CppStaticLibrary debugStaticLibrary = library.addStaticLibrary("debug" + linkageSuffix + operatingSystemSuffix, true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider(), debugVariant);
                                library.addStaticLibrary("release" + linkageSuffix + operatingSystemSuffix, true, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider(), releaseVariant);

                                if (!library.getLinkage().get().contains(Linkage.SHARED)) {
                                    // Use the debug static library as the development binary
                                    library.getDevelopmentBinary().set(debugStaticLibrary);
                                }
                            }
                        }
                    }
                }

                // All ligthweight variants created...

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

                library.getBinaries().realizeNow();
            }
        });
    }
}
