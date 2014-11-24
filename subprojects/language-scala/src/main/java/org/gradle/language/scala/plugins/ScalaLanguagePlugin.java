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
import org.gradle.jvm.JvmBinarySpec;
import org.gradle.jvm.JvmByteCode;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.jvm.plugins.JvmResourcesPlugin;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.language.scala.internal.DefaultScalaSourceSet;
import org.gradle.language.scala.tasks.PlatformScalaCompile;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.TransformationFileType;

import java.util.Collections;
import java.util.Map;


/**
 * Plugin for compiling Scala code. Applies the {@link org.gradle.language.base.plugins.ComponentModelBasePlugin} and {@link org.gradle.language.jvm.plugins.JvmResourcesPlugin}. Registers "scala"
 * language support with the {@link org.gradle.api.tasks.ScalaSourceSet}.
 */
@Incubating
public class ScalaLanguagePlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.apply(Collections.singletonMap("plugin", ComponentModelBasePlugin.class));
        project.apply(Collections.singletonMap("plugin", JvmResourcesPlugin.class));
    }


    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    static class Rules {
        @Mutate
        void registerLanguage(LanguageRegistry languages) {
            languages.add(new Scala());
        }
    }

    private static class Scala implements LanguageRegistration<ScalaLanguageSourceSet> {
        public String getName() {
            return "scala";
        }

        public Class<ScalaLanguageSourceSet> getSourceSetType() {
            return ScalaLanguageSourceSet.class;
        }

        public Class<? extends ScalaLanguageSourceSet> getSourceSetImplementation() {
            return DefaultScalaSourceSet.class;
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
                    return PlatformScalaCompile.class;
                }

                public void configureTask(Task task, BinarySpec binarySpec, LanguageSourceSet sourceSet) {
                    PlatformScalaCompile compile = (PlatformScalaCompile) task;
                    ScalaLanguageSourceSet scalaSourceSet = (ScalaLanguageSourceSet) sourceSet;
                    JvmBinarySpec binary = (JvmBinarySpec) binarySpec;

                    compile.setDescription(String.format("Compiles %s.", scalaSourceSet));
                    compile.setDestinationDir(binary.getClassesDir());
                    compile.setPlatform(binary.getTargetPlatform());

                    compile.setSource(scalaSourceSet.getSource());
                    compile.setClasspath(scalaSourceSet.getCompileClasspath().getFiles());
                    compile.setTargetCompatibility(binary.getTargetPlatform().getTargetCompatibility().toString());
                    compile.setSourceCompatibility(binary.getTargetPlatform().getTargetCompatibility().toString());

                    compile.dependsOn(scalaSourceSet);
                    binary.getTasks().getJar().dependsOn(compile);
                }
            };        }

        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof JvmBinarySpec;
        }
    }
}
