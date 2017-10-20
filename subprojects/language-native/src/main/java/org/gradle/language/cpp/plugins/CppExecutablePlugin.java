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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.AppliedPlugin;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.cpp.CppApplication;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.Linkage;
import org.gradle.language.cpp.internal.DefaultCppApplication;
import org.gradle.language.cpp.internal.MainExecutableVariant;
import org.gradle.language.cpp.internal.NativeVariant;

import javax.inject.Inject;

import static org.gradle.language.cpp.CppBinary.DEBUGGABLE_ATTRIBUTE;
import static org.gradle.language.cpp.CppBinary.LINKAGE_ATTRIBUTE;

/**
 * <p>A plugin that produces a native executable from C++ source.</p>
 *
 * <p>Assumes the source files are located in `src/main/cpp` and header files are located in `src/main/headers`.</p>
 *
 * <p>Adds a {@link CppComponent} extension to the project to allow configuration of the executable.</p>
 *
 * @since 4.1
 */
@Incubating
public class CppExecutablePlugin implements Plugin<ProjectInternal> {
    private final FileOperations fileOperations;

    /**
     * Injects a {@link FileOperations} instance.
     *
     * @since 4.2
     */
    @Inject
    public CppExecutablePlugin(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(CppBasePlugin.class);

        ConfigurationContainer configurations = project.getConfigurations();
        TaskContainer tasks = project.getTasks();
        ObjectFactory objectFactory = project.getObjects();

        // Add the application extension
        final CppApplication application = project.getExtensions().create(CppApplication.class, "executable", DefaultCppApplication.class,  "main", project.getLayout(), objectFactory, fileOperations, configurations);
        project.getComponents().add(application);
        project.getComponents().add(application.getDebugExecutable());
        project.getComponents().add(application.getReleaseExecutable());
        project.getComponents().add(application.getDebugStaticExecutable());
        project.getComponents().add(application.getReleaseStaticExecutable());

        // Configure the component
        application.getBaseName().set(project.getName());

        // Install the debug variant by default
        tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(application.getDevelopmentBinary().getInstallDirectory());

        // TODO - add lifecycle tasks to assemble each variant

        final Usage runtimeUsage = objectFactory.named(Usage.class, Usage.NATIVE_RUNTIME);

        final Configuration debugRuntimeElements = configurations.maybeCreate("debugRuntimeElements");
        debugRuntimeElements.extendsFrom(application.getImplementationDependencies());
        debugRuntimeElements.setCanBeResolved(false);
        debugRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
        debugRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, true);
        debugRuntimeElements.getAttributes().attribute(LINKAGE_ATTRIBUTE, Linkage.SHARED);
        debugRuntimeElements.getOutgoing().artifact(application.getDebugExecutable().getExecutableFile());

        final Configuration releaseRuntimeElements = configurations.maybeCreate("releaseRuntimeElements");
        releaseRuntimeElements.extendsFrom(application.getImplementationDependencies());
        releaseRuntimeElements.setCanBeResolved(false);
        releaseRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
        releaseRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, false);
        releaseRuntimeElements.getAttributes().attribute(LINKAGE_ATTRIBUTE, Linkage.SHARED);
        releaseRuntimeElements.getOutgoing().artifact(application.getReleaseExecutable().getExecutableFile());

        final Configuration debugStaticRuntimeElements = configurations.maybeCreate("debugRuntimeStaticElements");
        debugStaticRuntimeElements.extendsFrom(application.getImplementationDependencies());
        debugStaticRuntimeElements.setCanBeResolved(false);
        debugStaticRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
        debugStaticRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, true);
        debugStaticRuntimeElements.getAttributes().attribute(LINKAGE_ATTRIBUTE, Linkage.STATIC);
        debugStaticRuntimeElements.getOutgoing().artifact(application.getDebugStaticExecutable().getExecutableFile());

        final Configuration releaseStaticRuntimeElements = configurations.maybeCreate("releaseRuntimeStaticElements");
        releaseStaticRuntimeElements.extendsFrom(application.getImplementationDependencies());
        releaseStaticRuntimeElements.setCanBeResolved(false);
        releaseStaticRuntimeElements.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, runtimeUsage);
        releaseStaticRuntimeElements.getAttributes().attribute(DEBUGGABLE_ATTRIBUTE, false);
        releaseStaticRuntimeElements.getAttributes().attribute(LINKAGE_ATTRIBUTE, Linkage.STATIC);
        releaseStaticRuntimeElements.getOutgoing().artifact(application.getReleaseStaticExecutable().getExecutableFile());

        project.getPluginManager().withPlugin("maven-publish", new Action<AppliedPlugin>() {
            @Override
            public void execute(AppliedPlugin appliedPlugin) {
                final MainExecutableVariant mainVariant = new MainExecutableVariant();
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
                            }
                        });
                        extension.getPublications().create("debugShared", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(application.getBaseName().get() + "_debugShared");
                                publication.setVersion(project.getVersion().toString());
                                NativeVariant debugVariant = new NativeVariant("debugShared", runtimeUsage, debugRuntimeElements.getAllArtifacts(), debugRuntimeElements);
                                mainVariant.addVariant(debugVariant);
                                publication.from(debugVariant);
                            }
                        });
                        extension.getPublications().create("releaseShared", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(application.getBaseName().get() + "_releaseShared");
                                publication.setVersion(project.getVersion().toString());
                                NativeVariant releaseVariant = new NativeVariant("releaseShared", runtimeUsage, releaseRuntimeElements.getAllArtifacts(), releaseRuntimeElements);
                                mainVariant.addVariant(releaseVariant);
                                publication.from(releaseVariant);
                            }
                        });

                        extension.getPublications().create("debugStatic", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(application.getBaseName().get() + "_debugStatic");
                                publication.setVersion(project.getVersion().toString());
                                NativeVariant debugVariant = new NativeVariant("debugStatic", runtimeUsage, debugStaticRuntimeElements.getAllArtifacts(), debugStaticRuntimeElements);
                                mainVariant.addVariant(debugVariant);
                                publication.from(debugVariant);
                            }
                        });
                        extension.getPublications().create("releaseStatic", MavenPublication.class, new Action<MavenPublication>() {
                            @Override
                            public void execute(MavenPublication publication) {
                                // TODO - should track changes to these properties
                                publication.setGroupId(project.getGroup().toString());
                                publication.setArtifactId(application.getBaseName().get() + "_releaseStatic");
                                publication.setVersion(project.getVersion().toString());
                                NativeVariant releaseVariant = new NativeVariant("releaseStatic", runtimeUsage, releaseStaticRuntimeElements.getAllArtifacts(), releaseStaticRuntimeElements);
                                mainVariant.addVariant(releaseVariant);
                                publication.from(releaseVariant);
                            }
                        });
                    }
                });
            }
        });
    }
}
