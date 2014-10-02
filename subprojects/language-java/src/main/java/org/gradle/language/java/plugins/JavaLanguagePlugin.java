/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.java.plugins;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmByteCode;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.java.internal.DefaultJavaSourceSet;
import org.gradle.language.java.tasks.PlatformJavaCompile;
import org.gradle.language.jvm.plugins.JvmResourcesPlugin;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.TransformationFileType;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Plugin for compiling Java code. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin} and {@link org.gradle.language.jvm.plugins.JvmResourcesPlugin}. Registers "java"
 * language support with the {@link JavaSourceSet}.
 */
public class JavaLanguagePlugin implements Plugin<ProjectInternal> {

    public void apply(ProjectInternal project) {
        project.getPlugins().apply(ComponentModelBasePlugin.class);
        project.getPlugins().apply(JvmResourcesPlugin.class);
        project.getExtensions().getByType(LanguageRegistry.class).add(new Java());
    }

    private static class Java implements LanguageRegistration<JavaSourceSet> {
        public String getName() {
            return "java";
        }

        public Class<JavaSourceSet> getSourceSetType() {
            return JavaSourceSet.class;
        }

        public Class<? extends JavaSourceSet> getSourceSetImplementation() {
            return DefaultJavaSourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        public Class<? extends TransformationFileType> getOutputType() {
            return JvmByteCode.class;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                public String getTaskPrefix() {
                    return "compile";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return PlatformJavaCompile.class;
                }

                public void configureTask(Task task, BinarySpec binarySpec, LanguageSourceSet sourceSet) {
                    PlatformJavaCompile compile = (PlatformJavaCompile) task;
                    JavaSourceSet javaSourceSet = (JavaSourceSet) sourceSet;
                    JvmBinarySpec binary = (JvmBinarySpec) binarySpec;

                    compile.setDescription(String.format("Compiles %s.", javaSourceSet));
                    compile.setDestinationDir(binary.getClassesDir());
                    compile.setToolChain(binary.getToolChain());
                    compile.setPlatform(binary.getTargetPlatform());

                    compile.setSource(javaSourceSet.getSource());
                    compile.setClasspath(javaSourceSet.getCompileClasspath().getFiles());
                    compile.setTargetCompatibility(binary.getTargetPlatform().getTargetCompatibility().toString());
                    compile.setSourceCompatibility(binary.getTargetPlatform().getTargetCompatibility().toString());

                    compile.setDependencyCacheDir(new File(compile.getProject().getBuildDir(), "jvm-dep-cache"));
                    compile.dependsOn(javaSourceSet);
                    binary.getTasks().getJar().dependsOn(compile);
                }
            };
        }

        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof JvmBinarySpec;
        }
    }
}
