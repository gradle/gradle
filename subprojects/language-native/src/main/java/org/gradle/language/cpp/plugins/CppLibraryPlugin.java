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
import org.gradle.api.artifacts.ConfigurablePublishArtifact;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.DirectoryProperty;
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
import org.gradle.language.cpp.internal.DefaultCppLibrary;
import org.gradle.language.cpp.internal.MainLibraryVariant;
import org.gradle.language.cpp.internal.NativeRuntimeVariant;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.tasks.LinkSharedLibrary;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;

import javax.inject.Inject;
import java.io.File;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;

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
        DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();
        ObjectFactory objectFactory = project.getObjects();
        ProviderFactory providers = project.getProviders();

        // Add the library extension
        final CppLibrary library = project.getExtensions().create(CppLibrary.class, "library", DefaultCppLibrary.class, "main", project.getObjects(), fileOperations, project.getConfigurations());
        project.getComponents().add(library);
        project.getComponents().add(library.getDebugSharedLibrary());
        project.getComponents().add(library.getReleaseSharedLibrary());

        // Configure the component
        library.getBaseName().set(project.getName());

        // Define the outgoing artifacts
        // TODO - move this to the base plugin

        final LinkSharedLibrary linkDebug = (LinkSharedLibrary) tasks.getByName("linkDebug");
        // TODO - make this lazy, make a query method on the link task
        final PlatformToolProvider platformToolChain = ((NativeToolChainInternal) linkDebug.getToolChain()).select((NativePlatformInternal) linkDebug.getTargetPlatform());
        // TODO - should reflect changes to the task configuration
        Provider<RegularFile> debugLinkFile = buildDirectory.file(providers.provider(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return platformToolChain.getSharedLibraryLinkFileName("lib/main/debug/" + library.getBaseName().get());
            }
        }));

        final LinkSharedLibrary linkRelease = (LinkSharedLibrary) tasks.getByName("linkRelease");
        // TODO - should reflect changes to the task configuration
        Provider<RegularFile> releaseLinkFile = buildDirectory.file(providers.provider(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return platformToolChain.getSharedLibraryLinkFileName("lib/main/release/" + library.getBaseName().get());
            }
        }));

        tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(linkDebug);

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

        final Configuration debugLinkElements = configurations.maybeCreate("debugLinkElements");
        debugLinkElements.extendsFrom(implementation);
        debugLinkElements.setCanBeResolved(false);
        debugLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
        debugLinkElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, true);
        // TODO - should reflect changes to task output file
        debugLinkElements.getOutgoing().artifact(debugLinkFile, new Action<ConfigurablePublishArtifact>() {
            @Override
            public void execute(ConfigurablePublishArtifact artifact) {
                artifact.builtBy(linkDebug);
            }
        });

        final Configuration debugRuntimeElements = configurations.maybeCreate("debugRuntimeElements");
        debugRuntimeElements.extendsFrom(implementation);
        debugRuntimeElements.setCanBeResolved(false);
        debugRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
        debugRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, true);
        debugRuntimeElements.getOutgoing().artifact(linkDebug.getBinaryFile());

        final Configuration releaseLinkElements = configurations.maybeCreate("releaseLinkElements");
        releaseLinkElements.extendsFrom(implementation);
        releaseLinkElements.setCanBeResolved(false);
        releaseLinkElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, linkUsage);
        releaseLinkElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, false);
        // TODO - should reflect changes to task output file
        releaseLinkElements.getOutgoing().artifact(releaseLinkFile, new Action<ConfigurablePublishArtifact>() {
            @Override
            public void execute(ConfigurablePublishArtifact artifact) {
                artifact.builtBy(linkRelease);
            }
        });

        final Configuration releaseRuntimeElements = configurations.maybeCreate("releaseRuntimeElements");
        releaseRuntimeElements.extendsFrom(implementation);
        releaseRuntimeElements.setCanBeResolved(false);
        releaseRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
        releaseRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, false);
        releaseRuntimeElements.getOutgoing().artifact(linkRelease.getBinaryFile());

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
                        extension.getPublications().create("debug", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(library.getBaseName().get() + "_debug");
                                publication.setVersion(project.getVersion().toString());
                                NativeRuntimeVariant debugVariant = new NativeRuntimeVariant("debug", linkUsage, debugLinkElements, runtimeUsage, debugRuntimeElements);
                                // TODO - make release variant visible as well
                                mainVariant.addVariant(publication.getGroupId(), publication.getArtifactId(), publication.getVersion(), debugVariant);
                                publication.from(debugVariant);
                            }
                        });
                        extension.getPublications().create("release", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(library.getBaseName().get() + "_release");
                                publication.setVersion(project.getVersion().toString());
                                NativeRuntimeVariant releaseVariant = new NativeRuntimeVariant("release", linkUsage, releaseLinkElements, runtimeUsage, releaseRuntimeElements);
                                // TODO - make release variant visible as well. Needs better support in the dependency resolution engine and module metadata
                                mainVariant.addNonVisibleVariant(releaseVariant);
                                publication.from(releaseVariant);
                            }
                        });
                    }
                });
            }
        });
    }

}
