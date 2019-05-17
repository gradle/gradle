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

package org.gradle.language.scala.plugins;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmByteCode;
import org.gradle.jvm.internal.JvmAssembly;
import org.gradle.jvm.internal.WithJvmAssembly;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.JointCompileTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.jvm.plugins.JvmResourcesPlugin;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.language.scala.internal.DefaultScalaLanguageSourceSet;
import org.gradle.language.scala.internal.DefaultScalaPlatform;
import org.gradle.language.scala.internal.ScalaJvmAssembly;
import org.gradle.language.scala.tasks.PlatformScalaCompile;
import org.gradle.language.scala.toolchain.ScalaToolChain;
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
 * Plugin for compiling Scala code. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin} and {@link org.gradle.language.jvm.plugins.JvmResourcesPlugin}.
 * Registers "scala" language support with the {@link org.gradle.language.scala.ScalaLanguageSourceSet}.
 */
@Incubating
public class ScalaLanguagePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
        project.getPluginManager().apply(JvmResourcesPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        @Model
        ScalaToolChain scalaToolChain(ServiceRegistry serviceRegistry) {
            return serviceRegistry.get(ScalaToolChain.class);
        }

        @ComponentType
        void registerLanguage(TypeBuilder<ScalaLanguageSourceSet> builder) {
            builder.defaultImplementation(DefaultScalaLanguageSourceSet.class);
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages, ServiceRegistry serviceRegistry) {
            languages.add(new Scala());
        }
    }


    private static class Scala implements LanguageTransform<ScalaLanguageSourceSet, JvmByteCode> {
        @Override
        public String getLanguageName() {
            return "scala";
        }

        @Override
        public Class<ScalaLanguageSourceSet> getSourceSetType() {
            return ScalaLanguageSourceSet.class;
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
        public JointCompileTaskConfig getTransformTask() {
            return new JointCompileTaskConfig() {
                @Override
                public String getTaskPrefix() {
                    return "compile";
                }

                @Override
                public boolean canTransform(LanguageSourceSet candidate) {
                    return candidate instanceof ScalaLanguageSourceSet || candidate instanceof JavaSourceSet;
                }

                @Override
                public Class<? extends DefaultTask> getTaskType() {
                    return PlatformScalaCompile.class;
                }

                @Override
                public void configureTask(Task task, BinarySpec binarySpec, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
                    PlatformScalaCompile compile = (PlatformScalaCompile) task;
                    configureScalaTask(compile, ((WithJvmAssembly) binarySpec).getAssembly(), "Compiles " + sourceSet + ".");
                    addSourceSetToCompile(compile, sourceSet);
                    addSourceSetClasspath(compile, (ScalaLanguageSourceSet) sourceSet);
                }

                @Override
                public void configureAdditionalTransform(Task task, LanguageSourceSet sourceSet) {
                    PlatformScalaCompile compile = (PlatformScalaCompile) task;
                    addSourceSetToCompile(compile, sourceSet);
                }

                private void configureScalaTask(PlatformScalaCompile compile, JvmAssembly assembly, String description) {
                    assembly.builtBy(compile);

                    compile.setDescription(description);
                    compile.setDestinationDir(single(assembly.getClassDirectories()));

                    compile.getScalaCompileOptions().getIncrementalOptions().getAnalysisFile().set(
                        compile.getProject().getLayout().getBuildDirectory().file("tmp/scala/compilerAnalysis/" + compile.getName() + ".analysis")
                    );

                    JavaPlatform javaPlatform = assembly.getTargetPlatform();
                    String targetCompatibility = javaPlatform.getTargetCompatibility().toString();
                    compile.setTargetCompatibility(targetCompatibility);
                    compile.setSourceCompatibility(targetCompatibility);

                    if (assembly instanceof ScalaJvmAssembly) {
                        compile.setPlatform(((ScalaJvmAssembly) assembly).getScalaPlatform());
                    } else {
                        compile.setPlatform(new DefaultScalaPlatform());
                    }
                }

                private void addSourceSetToCompile(PlatformScalaCompile compile, LanguageSourceSet sourceSet) {
                    compile.dependsOn(sourceSet);
                    compile.source(sourceSet.getSource());
                }

                private void addSourceSetClasspath(PlatformScalaCompile compile, ScalaLanguageSourceSet scalaLanguageSourceSet) {
                    FileCollection classpath = scalaLanguageSourceSet.getCompileClasspath().getFiles();
                    compile.setClasspath(classpath);
                }

            };
        }

        @Override
        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof WithJvmAssembly;
        }
    }
}
