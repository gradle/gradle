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
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.language.base.BinariesContainer;
import org.gradle.language.base.internal.DefaultClasspath;
import org.gradle.api.internal.tasks.DefaultJavaSourceSet;
import org.gradle.language.base.internal.DefaultProjectSourceSet;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.jvm.ClassDirectoryBinary;
import org.gradle.language.jvm.JvmBinaryContainer;
import org.gradle.language.jvm.JvmLanguageSourceSet;
import org.gradle.language.jvm.plugins.JvmLanguagePlugin;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * Plugin for compiling Java code. Applies the {@link org.gradle.language.jvm.plugins.JvmLanguagePlugin}.
 * Adds a {@link JavaCompile} task for each {@link JavaSourceSet} added to a {@link org.gradle.language.jvm.ClassDirectoryBinary}.
 * Registers the {@link JavaSourceSet} element type for each {@link org.gradle.language.base.FunctionalSourceSet} added to {@link org.gradle.language.base.ProjectSourceSet}.
 */
@Incubating
public class JavaLanguagePlugin implements Plugin<Project> {
    private final Instantiator instantiator;

    @Inject
    public JavaLanguagePlugin(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void apply(final Project target) {
        final JvmLanguagePlugin jvmLanguagePlugin = target.getPlugins().apply(JvmLanguagePlugin.class);

        JvmBinaryContainer jvmBinaryContainer = (JvmBinaryContainer) target.getExtensions().getByType(BinariesContainer.class).getByName("jvm");
        jvmBinaryContainer.all(new Action<ClassDirectoryBinary>() {
            public void execute(final ClassDirectoryBinary binary) {
                binary.getSource().withType(JavaSourceSet.class).all(new Action<JavaSourceSet>() {
                    public void execute(JavaSourceSet javaSourceSet) {
                        // TODO: handle case where binary has multiple JavaSourceSet's
                        JavaCompile compileTask = target.getTasks().create(binary.getTaskName("compile", "java"), JavaCompile.class);
                        configureCompileTask(compileTask, javaSourceSet, binary);
                        binary.getClassesTask().dependsOn(compileTask);
                    }
                });
            }
        });

        ProjectSourceSet projectSourceSet = target.getExtensions().getByType(DefaultProjectSourceSet.class);
        projectSourceSet.all(new Action<FunctionalSourceSet>() {
            public void execute(final FunctionalSourceSet functionalSourceSet) {
                functionalSourceSet.registerFactory(JavaSourceSet.class, new NamedDomainObjectFactory<JavaSourceSet>() {
                    public JavaSourceSet create(String name) {
                        return instantiator.newInstance(DefaultJavaSourceSet.class, name,
                                instantiator.newInstance(DefaultSourceDirectorySet.class),
                                instantiator.newInstance(DefaultClasspath.class), functionalSourceSet);
                    }
                });
            }
        });
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
