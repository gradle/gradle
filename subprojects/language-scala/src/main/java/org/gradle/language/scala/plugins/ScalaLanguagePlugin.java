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

import org.gradle.api.*;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmByteCode;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.jvm.plugins.JvmResourcesPlugin;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.language.scala.internal.DefaultScalaLanguageSourceSet;
import org.gradle.language.scala.internal.DefaultScalaPlatform;
import org.gradle.language.scala.tasks.PlatformScalaCompile;
import org.gradle.language.scala.toolchain.ScalaToolChain;
import org.gradle.model.Model;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;

import java.io.File;
import java.util.Collections;
import java.util.Map;


/**
 * Plugin for compiling Scala code. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin} and {@link org.gradle.language.jvm.plugins.JvmResourcesPlugin}.
 * Registers "scala" language support with the {@link org.gradle.language.scala.ScalaLanguageSourceSet}.
 */
@Incubating
public class ScalaLanguagePlugin implements Plugin<Project> {

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

        @LanguageType
        void registerLanguage(LanguageTypeBuilder<ScalaLanguageSourceSet> builder) {
            builder.setLanguageName("scala");
            builder.defaultImplementation(DefaultScalaLanguageSourceSet.class);
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages, ServiceRegistry serviceRegistry) {
            languages.add(new Scala());
        }
    }

    private static class Scala implements LanguageTransform<ScalaLanguageSourceSet, JvmByteCode> {
        public Class<ScalaLanguageSourceSet> getSourceSetType() {
            return ScalaLanguageSourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        public Class<JvmByteCode> getOutputType() {
            return JvmByteCode.class;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                public String getTaskPrefix() {
                    return "compile";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return PlatformScalaCompile.class;
                }

                public void configureTask(Task task, BinarySpec binarySpec, LanguageSourceSet sourceSet) {
                    PlatformScalaCompile compile = (PlatformScalaCompile) task;
                    ScalaLanguageSourceSet scalaSourceSet = (ScalaLanguageSourceSet) sourceSet;
                    JvmBinarySpec binary = (JvmBinarySpec) binarySpec;
                    JavaPlatform javaPlatform = binary.getTargetPlatform();
                    // TODO RG resolve the scala platform from the binary

                    compile.setPlatform(new DefaultScalaPlatform("2.10.4"));
                    File analysisFile = new File(task.getTemporaryDir(), String.format("compilerAnalysis/%s.analysis", task.getName()));
                    compile.getScalaCompileOptions().getIncrementalOptions().setAnalysisFile(analysisFile);

                    compile.setDescription(String.format("Compiles %s.", scalaSourceSet));
                    compile.setDestinationDir(binary.getClassesDir());

                    compile.setSource(scalaSourceSet.getSource());
                    compile.setClasspath(scalaSourceSet.getCompileClasspath().getFiles());
                    compile.setTargetCompatibility(javaPlatform.getTargetCompatibility().toString());
                    compile.setSourceCompatibility(javaPlatform.getTargetCompatibility().toString());

                    compile.dependsOn(scalaSourceSet);
                    binary.getTasks().getJar().dependsOn(compile);
                }
            };
        }

        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof JvmBinarySpec;
        }
    }
}
