/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.language.gosu.plugins;

import org.gradle.api.*;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmByteCode;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.gosu.internal.GosuJvmAssembly;
import org.gradle.language.jvm.plugins.JvmResourcesPlugin;
import org.gradle.language.gosu.GosuLanguageSourceSet;
import org.gradle.language.gosu.internal.DefaultGosuLanguageSourceSet;
import org.gradle.language.gosu.internal.DefaultGosuPlatform;
import org.gradle.language.gosu.tasks.PlatformGosuCompile;
import org.gradle.language.gosu.toolchain.GosuToolChain;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;

import java.util.Collections;
import java.util.Map;

import static org.gradle.util.CollectionUtils.single;

/**
 * Plugin for compiling Gosu code. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin} and {@link org.gradle.language.jvm.plugins.JvmResourcesPlugin}.
 * Registers "gosu" language support with the {@link org.gradle.language.gosu.GosuLanguageSourceSet}.
 */
@Incubating
public class GosuLanguagePlugin implements Plugin<Project> {

    public void apply(Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
        project.getPluginManager().apply(JvmResourcesPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        @Model
        GosuToolChain gosuToolChain(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(GosuToolChain.class);
        }

        @ComponentType
        void registerLanguage(TypeBuilder<GosuLanguageSourceSet> builder) {
            builder.defaultImplementation(DefaultGosuLanguageSourceSet.class);
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages, ServiceRegistry serviceRegistry) {
            languages.add(new Gosu());
        }
    }

    private static class Gosu implements LanguageTransform<GosuLanguageSourceSet, JvmByteCode> {
        @Override
        public String getLanguageName() {
            return "gosu";
        }

        @Override
        public Class<GosuLanguageSourceSet> getSourceSetType() {
            return GosuLanguageSourceSet.class;
        }

        @Override
        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        @Override
        public Class<JvmByteCode> getOutputType() {
            return JvmByteCode.class;
        }

        @Override
        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                public String getTaskPrefix() {
                    return "compile";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return PlatformGosuCompile.class;
                }

                public void configureTask(Task task, BinarySpec binarySpec, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
                    PlatformGosuCompile compile = (PlatformGosuCompile) task;
                    configureGosuTask(compile, ((WithJvmAssembly) binarySpec).getAssembly(), String.format("Compiles %s.", sourceSet));
                    addSourceSetToCompile(compile, sourceSet);
                    addSourceSetClasspath(compile, (GosuLanguageSourceSet) sourceSet);
                }

                private void configureGosuTask(PlatformGosuCompile compile, JvmAssembly assembly, String description) {
                    assembly.builtBy(compile);

                    compile.setDescription(description);
                    compile.setDestinationDir(single(assembly.getClassDirectories()));

                    JavaPlatform javaPlatform = assembly.getTargetPlatform();
                    String targetCompatibility = javaPlatform.getTargetCompatibility().toString();
                    compile.setTargetCompatibility(targetCompatibility);
                    compile.setSourceCompatibility(targetCompatibility);

                    if (assembly instanceof GosuJvmAssembly) {
                        compile.setPlatform(((GosuJvmAssembly) assembly).getGosuPlatform());
                    } else {
                        compile.setPlatform(new DefaultGosuPlatform("1.13.1"));
                    }
                }

                private void addSourceSetToCompile(PlatformGosuCompile compile, LanguageSourceSet sourceSet) {
                    compile.dependsOn(sourceSet);
                    compile.source(sourceSet.getSource());
                }

                private void addSourceSetClasspath(PlatformGosuCompile compile, GosuLanguageSourceSet gosuLanguageSourceSet) {
                    FileCollection classpath = gosuLanguageSourceSet.getCompileClasspath().getFiles();
                    compile.setClasspath(classpath);
                }

            };
        }

        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof WithJvmAssembly;
        }
    }
}
