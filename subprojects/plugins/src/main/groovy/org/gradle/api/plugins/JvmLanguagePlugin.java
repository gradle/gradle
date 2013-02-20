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
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.plugins.ProcessResources;
import org.gradle.api.internal.tasks.DefaultBinariesContainer;
import org.gradle.api.internal.tasks.DefaultJvmBinaryContainer;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Base plugin for JVM language support. Applies the {@link LanguageBasePlugin}. Adds a {@code classes} task for each
 * {@link ClassDirectoryBinary}, and a {@code processResources} task for each {@link ResourceSet} added to a
 * {@link ClassDirectoryBinary}.
 */
public class JvmLanguagePlugin implements Plugin<Project> {
    private final Instantiator instantiator;

    @Inject
    public JvmLanguagePlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void apply(final Project target) {
        target.getPlugins().apply(LanguageBasePlugin.class);
        JvmBinaryContainer container = instantiator.newInstance(DefaultJvmBinaryContainer.class, instantiator);
        BinaryContainer extension = instantiator.newInstance(DefaultBinariesContainer.class, container);
        target.getExtensions().add("binaries", extension);
        container.all(new Action<ClassDirectoryBinary>() {
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
