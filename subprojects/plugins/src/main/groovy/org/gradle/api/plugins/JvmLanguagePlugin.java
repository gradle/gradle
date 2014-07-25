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
import org.gradle.api.internal.jvm.ClassDirectoryBinarySpecInternal;
import org.gradle.api.internal.jvm.DefaultClassDirectoryBinarySpec;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.jvm.ClassDirectoryBinarySpec;
import org.gradle.api.tasks.Copy;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.plugins.LanguageBasePlugin;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.jvm.ResourceSet;
import org.gradle.language.jvm.internal.DefaultResourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.base.internal.BinaryNamingScheme;
import org.gradle.runtime.jvm.toolchain.JavaToolChain;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Base plugin for JVM language support. Applies the {@link org.gradle.language.base.plugins.LanguageBasePlugin}.
 * Registers the {@link org.gradle.api.jvm.ClassDirectoryBinarySpec} element type for the {@link org.gradle.runtime.base.BinaryContainer}.
 * Adds a lifecycle task named {@code classes} for each {@link org.gradle.api.jvm.ClassDirectoryBinarySpec}.
 * Registers the {@link org.gradle.language.jvm.ResourceSet} element type for each {@link org.gradle.language.base.FunctionalSourceSet} added to {@link org.gradle.language.base.ProjectSourceSet}.
 * Adds a {@link Copy} task named {@code processXYZResources} for each {@link org.gradle.language.jvm.ResourceSet} added to a {@link org.gradle.api.jvm.ClassDirectoryBinarySpec}.
 */
@Incubating
public class JvmLanguagePlugin implements Plugin<Project> {
    private final Instantiator instantiator;
    private final FileResolver fileResolver;
    private final JavaToolChain toolChain;

    @Inject
    public JvmLanguagePlugin(Instantiator instantiator, FileResolver fileResolver, JavaToolChain toolChain) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
        this.toolChain = toolChain;
    }

    public void apply(final Project target) {
        target.getPlugins().apply(LanguageBasePlugin.class);

        ProjectSourceSet projectSourceSet = target.getExtensions().getByType(ProjectSourceSet.class);
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

        BinaryContainer binaryContainer = target.getExtensions().getByType(BinaryContainer.class);
        binaryContainer.registerFactory(ClassDirectoryBinarySpec.class, new NamedDomainObjectFactory<ClassDirectoryBinarySpec>() {
            public ClassDirectoryBinarySpec create(String name) {
                return instantiator.newInstance(DefaultClassDirectoryBinarySpec.class, name, toolChain);
            }
        });

        binaryContainer.withType(ClassDirectoryBinarySpecInternal.class).all(new Action<ClassDirectoryBinarySpecInternal>() {
            public void execute(ClassDirectoryBinarySpecInternal binary) {
                Task binaryLifecycleTask = target.task(binary.getNamingScheme().getLifecycleTaskName());
                binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
                binary.setBuildTask(binaryLifecycleTask);
            }
        });

        binaryContainer.withType(ClassDirectoryBinarySpecInternal.class).all(new Action<ClassDirectoryBinarySpecInternal>() {
            public void execute(final ClassDirectoryBinarySpecInternal binary) {
                final BinaryNamingScheme namingScheme = binary.getNamingScheme();
                ConventionMapping conventionMapping = new DslObject(binary).getConventionMapping();
                conventionMapping.map("classesDir", new Callable<File>() {
                    public File call() throws Exception {
                        return new File(new File(target.getBuildDir(), "classes"), namingScheme.getOutputDirectoryBase());
                    }
                });
                binary.getSource().withType(ResourceSet.class).all(new Action<ResourceSet>() {
                    public void execute(ResourceSet resourceSet) {
                        // TODO: handle case where binary has multiple ResourceSet's
                        Copy resourcesTask = target.getTasks().create(namingScheme.getTaskName("process", "resources"), ProcessResources.class);
                        resourcesTask.setDescription(String.format("Processes %s.", resourceSet));
                        new DslObject(resourcesTask).getConventionMapping().map("destinationDir", new Callable<File>() {
                            public File call() throws Exception {
                                return binary.getResourcesDir();
                            }
                        });
                        binary.getTasks().add(resourcesTask);
                        binary.builtBy(resourcesTask);
                        resourcesTask.from(resourceSet.getSource());
                    }
                });
            }
        });
    }
}
