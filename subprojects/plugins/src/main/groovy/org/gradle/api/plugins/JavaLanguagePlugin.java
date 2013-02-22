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
import org.gradle.api.tasks.BinaryContainer;
import org.gradle.api.tasks.ClassDirectoryBinary;
import org.gradle.api.tasks.JavaSourceSet;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Plugin for compiling Java code. Applies the {@link JvmLanguagePlugin}.
 * Adds a {@link JavaCompile} task for each {@link JavaSourceSet} added to a {@link ClassDirectoryBinary}.
 */
@Incubating
public class JavaLanguagePlugin implements Plugin<Project> {
    public void apply(final Project target) {
        final JvmLanguagePlugin jvmLanguagePlugin = target.getPlugins().apply(JvmLanguagePlugin.class);
        BinaryContainer binaryContainer = target.getExtensions().getByType(BinaryContainer.class);
        binaryContainer.getJvm().all(new Action<ClassDirectoryBinary>() {
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
    }
}
