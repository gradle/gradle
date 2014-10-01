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
import org.gradle.api.internal.jvm.ClassDirectoryBinarySpecInternal;
import org.gradle.api.internal.jvm.DefaultClassDirectoryBinarySpec;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.jvm.ClassDirectoryBinarySpec;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.plugins.LanguageBasePlugin;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.jvm.JvmResourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.platform.base.internal.BinaryNamingScheme;
import org.gradle.jvm.toolchain.JavaToolChain;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Plugin for compiling Java code. Applies the {@link org.gradle.language.base.plugins.LanguageBasePlugin}.
 *
 * Base plugin for Java language support. Applies the {@link org.gradle.language.base.plugins.LanguageBasePlugin}.
 * Registers the {@link org.gradle.jvm.ClassDirectoryBinarySpec} element type for the {@link org.gradle.platform.base.BinaryContainer}.
 * Adds a lifecycle task named {@code classes} for each {@link org.gradle.jvm.ClassDirectoryBinarySpec}.
 * Adds a {@link JavaCompile} task for each {@link JavaSourceSet} added to a {@link org.gradle.jvm.ClassDirectoryBinarySpec}.
 */
@Incubating
public class LegacyJavaComponentPlugin implements Plugin<Project> {

    private final Instantiator instantiator;
    private final JavaToolChain toolChain;

    @Inject
    public LegacyJavaComponentPlugin(Instantiator instantiator, JavaToolChain toolChain) {
        this.instantiator = instantiator;
        this.toolChain = toolChain;
    }

    public void apply(final Project target) {

        target.getPlugins().apply(LanguageBasePlugin.class);
        BinaryContainer binaryContainer = target.getExtensions().getByType(BinaryContainer.class);
        binaryContainer.registerFactory(ClassDirectoryBinarySpec.class, new NamedDomainObjectFactory<ClassDirectoryBinarySpec>() {
            public ClassDirectoryBinarySpec create(String name) {
                return instantiator.newInstance(DefaultClassDirectoryBinarySpec.class, name, toolChain, new DefaultJavaPlatform(JavaVersion.current()));
            }
        });

        binaryContainer.withType(ClassDirectoryBinarySpecInternal.class).all(new Action<ClassDirectoryBinarySpecInternal>() {
            public void execute(ClassDirectoryBinarySpecInternal binary) {
                createBinaryLifecycleTask(binary, target);
                setClassesDirConvention(binary, target);
                createProcessResourcesTaskForBinary(binary, target);
                createCompileJavaTaskForBinary(binary, target);
            }
        });
    }

    private void setClassesDirConvention(ClassDirectoryBinarySpecInternal binary, final Project target) {
        final BinaryNamingScheme namingScheme = binary.getNamingScheme();
        ConventionMapping conventionMapping = new DslObject(binary).getConventionMapping();
        conventionMapping.map("classesDir", new Callable<File>() {
            public File call() throws Exception {
                return new File(new File(target.getBuildDir(), "classes"), namingScheme.getOutputDirectoryBase());
            }
        });
    }

    private void createCompileJavaTaskForBinary(final ClassDirectoryBinarySpecInternal binary, final Project target) {
        final BinaryNamingScheme namingScheme = binary.getNamingScheme();
        binary.getSource().withType(JavaSourceSet.class).all(new Action<JavaSourceSet>() {
            public void execute(JavaSourceSet javaSourceSet) {
                JavaCompile compileTask = target.getTasks().create(namingScheme.getTaskName("compile", "java"), JavaCompile.class);
                configureCompileTask(compileTask, javaSourceSet, binary);
                binary.getTasks().add(compileTask);
                binary.builtBy(compileTask);
            }
        });
    }

    private void createProcessResourcesTaskForBinary(final ClassDirectoryBinarySpecInternal binary, final Project target) {
        final BinaryNamingScheme namingScheme = binary.getNamingScheme();
        binary.getSource().withType(JvmResourceSet.class).all(new Action<JvmResourceSet>() {
            public void execute(JvmResourceSet resourceSet) {
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

    private void createBinaryLifecycleTask(ClassDirectoryBinarySpecInternal binary, Project target) {
        Task binaryLifecycleTask = target.task(binary.getNamingScheme().getLifecycleTaskName());
        binaryLifecycleTask.setGroup(LifecycleBasePlugin.BUILD_GROUP);
        binaryLifecycleTask.setDescription(String.format("Assembles %s.", binary));
        binary.setBuildTask(binaryLifecycleTask);
    }


    /**
     * Preconfigures the specified compile task based on the specified source set and class directory binary.
     *
     * @param compile the compile task to be preconfigured
     * @param sourceSet the source set for the compile task
     * @param binary the binary for the compile task
     */
    public void configureCompileTask(AbstractCompile compile, final JavaSourceSet sourceSet, final ClassDirectoryBinarySpec binary) {
        compile.setDescription(String.format("Compiles %s.", sourceSet));
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
