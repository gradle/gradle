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
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.tasks.DefaultClasspath;
import org.gradle.api.internal.tasks.DefaultJavaSourceSet;
import org.gradle.api.internal.tasks.DefaultProjectSourceSet;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;

/**
 * Plugin for compiling Java code. Applies the {@link JvmLanguagePlugin}.
 * Adds a {@link JavaCompile} task for each {@link JavaSourceSet} added to a {@link ClassDirectoryBinary}.
 * Registers the {@link JavaSourceSet} element type for each {@link FunctionalSourceSet} added to {@link ProjectSourceSet}.
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

        JvmBinaryContainer jvmBinaryContainer = jvmLanguagePlugin.getJvmBinaryContainer();
        jvmBinaryContainer.all(new Action<ClassDirectoryBinary>() {
            public void execute(final ClassDirectoryBinary binary) {
                binary.getSource().withType(JavaSourceSet.class).all(new Action<JavaSourceSet>() {
                    public void execute(JavaSourceSet javaSourceSet) {
                        JavaCompile compileTask = (JavaCompile) binary.getCompileTask(); // TODO: can't simply cast
                        if (compileTask == null) {
                            compileTask = target.getTasks().add(binary.getTaskName("compile", "java"), JavaCompile.class);
                            jvmLanguagePlugin.configureCompileTask(compileTask, javaSourceSet, binary);
                            binary.setCompileTask(compileTask);
                            binary.getClassesTask().dependsOn(compileTask);
                        }
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
}
