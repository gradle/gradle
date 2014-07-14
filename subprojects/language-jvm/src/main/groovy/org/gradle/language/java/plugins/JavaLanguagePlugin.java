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

import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.java.internal.DefaultJavaSourceSet;
import org.gradle.language.jvm.plugins.JvmResourcesPlugin;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.runtime.base.BinaryContainer;
import org.gradle.runtime.jvm.internal.ProjectJarBinaryInternal;

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

    /**
     * Model rules.
     */
    @RuleSource
    static class Rules {
        @Mutate
        @SuppressWarnings("UnusedDeclaration")
        void createTasks(final TaskContainer tasks, BinaryContainer binaries, ServiceRegistry serviceRegistry) {
            FileLookup fileLookup = serviceRegistry.get(FileLookup.class);
            for (ProjectJarBinaryInternal binary : binaries.withType(ProjectJarBinaryInternal.class)) {
                for (JavaSourceSet javaSourceSet : binary.getSource().withType(JavaSourceSet.class)) {

                    String compileTaskName = binary.getNamingScheme().getTaskName("compile", ((LanguageSourceSetInternal) javaSourceSet).getFullName());
                    JavaCompile compile = tasks.create(compileTaskName, JavaCompile.class);
                    compile.setDescription(String.format("Compiles %s.", javaSourceSet));
                    compile.setDestinationDir(binary.getClassesDir());
                    compile.setToolChain(binary.getToolChain());

                    compile.setSource(javaSourceSet.getSource());
                    compile.setClasspath(javaSourceSet.getCompileClasspath().getFiles());
                    compile.setSourceCompatibility(JavaVersion.current().toString());
                    compile.setTargetCompatibility(JavaVersion.current().toString());
                    compile.setDependencyCacheDir(fileLookup.getFileResolver(compile.getProject().getBuildDir()).resolve("jvm-dep-cache"));
                    compile.dependsOn(javaSourceSet);

                    binary.getTasks().add(compile);
                    binary.getTasks().getJar().dependsOn(compile);
                }
            }
        }
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
    }
}
