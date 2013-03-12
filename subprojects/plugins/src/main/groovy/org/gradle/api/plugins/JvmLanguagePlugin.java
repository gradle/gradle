/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.*;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.plugins.ProcessResources;
import org.gradle.api.internal.tasks.*;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Base plugin for JVM language support. Applies the {@link LanguageBasePlugin}.
 * Adds a {@link JvmBinaryContainer} named {@code jvm} to the project's {@link BinariesContainer}.
 * Registers the {@link ClassDirectoryBinary} element type for that container.
 * Adds a lifecycle task named {@code classes} for each {@link ClassDirectoryBinary}.
 * Adds a {@link Copy} task named {@code processXYZResources} for each {@link ResourceSet} added to a {@link ClassDirectoryBinary}.
 */
@Incubating
public class JvmLanguagePlugin implements Plugin<Project> {
    private final Instantiator instantiator;
    private final FileResolver fileResolver;

    private JvmBinaryContainer jvmBinaryContainer;

    @Inject
    public JvmLanguagePlugin(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
    }

    public void apply(final Project target) {
        target.getPlugins().apply(LanguageBasePlugin.class);

        ProjectSourceSet projectSourceSet = target.getExtensions().getByType(DefaultProjectSourceSet.class);
        projectSourceSet.all(new Action<FunctionalSourceSet>() {
            public void execute(final FunctionalSourceSet functionalSourceSet) {
                functionalSourceSet.registerFactory(ResourceSet.class, new NamedDomainObjectFactory<ResourceSet>() {
                    public ResourceSet create(String name) {
                        return instantiator.newInstance(DefaultResourceSet.class, name,
                                instantiator.newInstance(DefaultSourceDirectorySet.class, name, fileResolver), functionalSourceSet);
                    }
                });
            }
        });

        BinariesContainer binariesContainer = target.getExtensions().getByType(DefaultBinariesContainer.class);
        jvmBinaryContainer = instantiator.newInstance(DefaultJvmBinaryContainer.class, instantiator);
        binariesContainer.add(jvmBinaryContainer);

        jvmBinaryContainer.registerFactory(ClassDirectoryBinary.class, new NamedDomainObjectFactory<ClassDirectoryBinary>() {
            public ClassDirectoryBinary create(String name) {
                return instantiator.newInstance(DefaultClassDirectoryBinary.class, name);
            };
        });

        jvmBinaryContainer.all(new Action<ClassDirectoryBinary>() {
            public void execute(final ClassDirectoryBinary binary) {
                ConventionMapping conventionMapping = new DslObject(binary).getConventionMapping();
                conventionMapping.map("classesDir", new Callable<File>() {
                    public File call() throws Exception {
                        return new File(new File(target.getBuildDir(), "classes"), binary.getName());
                    }
                });
                final Task classesTask = target.getTasks().add(binary.getTaskName(null, "classes"));
                classesTask.setDescription(String.format("Assembles the %s classes.", binary.getName()));
                binary.setClassesTask(classesTask);
                binary.getSource().withType(ResourceSet.class).all(new Action<ResourceSet>() {
                    public void execute(ResourceSet resourceSet) {
                        Copy resourcesTask = binary.getResourcesTask();
                        if (resourcesTask == null) {
                            resourcesTask = target.getTasks().add(binary.getTaskName("process", "resources"), ProcessResources.class);
                            resourcesTask.setDescription(String.format("Processes the %s resources.", binary.getName()));
                            new DslObject(resourcesTask).getConventionMapping().map("destinationDir", new Callable<File>() {
                                public File call() throws Exception {
                                    return binary.getResourcesDir();
                                }
                            });
                            binary.setResourcesTask(resourcesTask);
                            classesTask.dependsOn(resourcesTask);
                        }
                        resourcesTask.from(resourceSet.getSource());
                    }
                });
            }
        });
    }

    /**
     * Returns the {@code binaries.jvm} container that was added by this plugin to the project.
     *
     * @return the {@code binaries.jvm} container that was added by this plugin to the project
     */
    public JvmBinaryContainer getJvmBinaryContainer() {
        return jvmBinaryContainer;
    }

    /**
     * Preconfigures the specified compile task based on the specified source set and class directory binary.
     *
     * @param compile the compile task to be preconfigured
     * @param sourceSet the source set for the compile task
     * @param binary the binary for the compile task
     */
    public void configureCompileTask(AbstractCompile compile, final JvmLanguageSourceSet sourceSet, final ClassDirectoryBinary binary) {
        compile.setDescription(String.format("Compiles the %s.", sourceSet));
        compile.setSource(sourceSet.getSource());
        compile.dependsOn(sourceSet);
        ConventionMapping conventionMapping = compile.getConventionMapping();
        conventionMapping.map("classpath", new Callable<Object>() {
            public Object call() throws Exception {
                return sourceSet.getCompileClasspath().getFiles();
            }
        });
        conventionMapping.map("destinationDir", new Callable<Object>() {
            public Object call() throws Exception {
                return binary.getClassesDir();
            }
        });
    }
}
