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
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppExecutable;
import org.gradle.language.cpp.CppPlatform;
import org.gradle.language.cpp.internal.DefaultCppApplication;
import org.gradle.language.cpp.internal.MainExecutableVariant;
import org.gradle.language.cpp.internal.NativeVariant;
import org.gradle.language.internal.NativeComponentFactory;
import org.gradle.language.nativeplatform.internal.toolchains.ToolChainSelector;

import javax.inject.Inject;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.OPTIMIZED_ATTRIBUTE;

/**
 * <p>A plugin that produces a native application from C++ source.</p>
 *
 * <p>Assumes the source files are located in `src/main/cpp` and header files are located in `src/main/headers`.</p>
 *
 * <p>Adds a {@link CppApplication} extension to the project to allow configuration of the application.</p>
 *
 * @since 4.5
 */
@Incubating
public class CppApplicationPlugin implements Plugin<ProjectInternal> {
    private final NativeComponentFactory componentFactory;
    private final ToolChainSelector toolChainSelector;

    @Inject
    public CppApplicationPlugin(NativeComponentFactory componentFactory, ToolChainSelector toolChainSelector) {
        this.componentFactory = componentFactory;
        this.toolChainSelector = toolChainSelector;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);

        final ConfigurationContainer configurations = project.getConfigurations();
        final ObjectFactory objectFactory = project.getObjects();

        // Add the application and extension
        final DefaultCppApplication application = componentFactory.newInstance(CppApplication.class, DefaultCppApplication.class, "main");
        project.getExtensions().add(CppApplication.class, "application", application);
        project.getComponents().add(application);

        // Configure the component
        application.getBaseName().set(project.getName());

        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                ToolChainSelector.Result<CppPlatform> result = toolChainSelector.select(CppPlatform.class);

                CppExecutable debugExecutable = application.addExecutable("debug", true, false, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());
                CppExecutable releaseExecutable = application.addExecutable("release", true, true, result.getTargetPlatform(), result.getToolChain(), result.getPlatformToolProvider());

                // Use the debug variant as the development binary
                application.getDevelopmentBinary().set(debugExecutable);

                // TODO - add lifecycle tasks to assemble each variant

                final Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);

                final Configuration debugRuntimeElements = configurations.maybeCreate("debugRuntimeElements");
                debugRuntimeElements.extendsFrom(application.getImplementationDependencies());
                debugRuntimeElements.setCanBeResolved(false);
                debugRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                debugRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, debugExecutable.isDebuggable());
                debugRuntimeElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, debugExecutable.isOptimized());
                debugRuntimeElements.getOutgoing().artifact(debugExecutable.getExecutableFile());

                final Configuration releaseRuntimeElements = configurations.maybeCreate("releaseRuntimeElements");
                releaseRuntimeElements.extendsFrom(application.getImplementationDependencies());
                releaseRuntimeElements.setCanBeResolved(false);
                releaseRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
                releaseRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, releaseExecutable.isDebuggable());
                releaseRuntimeElements.getAttributes().attribute(OPTIMIZED_ATTRIBUTE, releaseExecutable.isOptimized());
                releaseRuntimeElements.getOutgoing().artifact(releaseExecutable.getExecutableFile());

                final MainExecutableVariant mainVariant = new MainExecutableVariant();
                NativeVariant debugVariant = new NativeVariant("debug", runtimeUsage, debugRuntimeElements.getAllArtifacts(), debugRuntimeElements);
                mainVariant.addVariant(debugVariant);
                NativeVariant releaseVariant = new NativeVariant("release", runtimeUsage, releaseRuntimeElements.getAllArtifacts(), releaseRuntimeElements);
                mainVariant.addVariant(releaseVariant);

                project.getPluginManager().withPlugin("maven-publish", new Action<AppliedPlugin>() {
                    @Override
                    public void execute(AppliedPlugin appliedPlugin) {
                        project.getExtensions().configure(PublishingExtension.class, new Action<PublishingExtension>() {
                            @Override
                            public void execute(PublishingExtension extension) {
                                extension.getPublications().create("main", MavenPublication.class, new Action<MavenPublication>() {
                                    @Override
                                    public void execute(MavenPublication publication) {
                                        // TODO - should track changes to these properties
                                        publication.setGroupId(project.getGroup().toString());
                                        publication.setArtifactId(application.getBaseName().get());
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
                                            publication.setArtifactId(application.getBaseName().get() + "_" + child.getName());
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

                application.getBinaries().realizeNow();
            }
        });
    }
}
