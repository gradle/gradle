/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.play.plugins;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.routes.RoutesSourceSet;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.internal.ScalaSourceCode;
import org.gradle.play.tasks.RoutesCompile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/**
 * Plugin for compiling Play routes sources in a Play application.
 */
@SuppressWarnings("UnusedDeclaration")
@Incubating
public class PlayRoutesPlugin extends RuleSource {

    @ComponentType
    void registerRoutesLanguageType(TypeBuilder<RoutesSourceSet> builder) {
    }

    @Mutate
    void createGeneratedScalaSourceSets(@Path("binaries") ModelMap<PlayApplicationBinarySpecInternal> binaries, final SourceDirectorySetFactory sourceDirectorySetFactory) {
        binaries.all(new Action<PlayApplicationBinarySpecInternal>() {
            @Override
            public void execute(PlayApplicationBinarySpecInternal playApplicationBinarySpec) {
                for (LanguageSourceSet languageSourceSet : playApplicationBinarySpec.getInputs().withType(RoutesSourceSet.class)) {
                    playApplicationBinarySpec.addGeneratedScala(languageSourceSet, sourceDirectorySetFactory);
                }
            }
        });
    }

    @Mutate
    void registerLanguageTransform(LanguageTransformContainer languages) {
        languages.add(new Routes());
    }

    private static class Routes implements LanguageTransform<RoutesSourceSet, ScalaSourceCode> {
        @Override
        public String getLanguageName() {
            return "routes";
        }

        @Override
        public Class<RoutesSourceSet> getSourceSetType() {
            return RoutesSourceSet.class;
        }

        @Override
        public Class<ScalaSourceCode> getOutputType() {
            return ScalaSourceCode.class;
        }

        @Override
        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        @Override
        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                public String getTaskPrefix() {
                    return "compile";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return RoutesCompile.class;
                }

                public void configureTask(Task task, BinarySpec binarySpec, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
                    PlayApplicationBinarySpecInternal binary = (PlayApplicationBinarySpecInternal) binarySpec;
                    RoutesSourceSet routesSourceSet = (RoutesSourceSet) sourceSet;
                    RoutesCompile routesCompile = (RoutesCompile) task;
                    ScalaLanguageSourceSet routesScalaSources = binary.getGeneratedScala().get(routesSourceSet);
                    File generatedSourceDir = binary.getNamingScheme().getOutputDirectory(task.getProject().getBuildDir(), "src");
                    File routesCompileOutputDirectory = new File(generatedSourceDir, routesScalaSources.getName());

                    routesCompile.setDescription("Generates routes for the '" + routesSourceSet.getName() + "' source set.");
                    routesCompile.setPlatform(binary.getTargetPlatform());
                    routesCompile.setAdditionalImports(new ArrayList<String>());
                    routesCompile.setSource(routesSourceSet.getSource());
                    routesCompile.setOutputDirectory(routesCompileOutputDirectory);
                    routesCompile.setInjectedRoutesGenerator(binary.getApplication().getInjectedRoutesGenerator());

                    routesScalaSources.getSource().srcDir(routesCompileOutputDirectory);
                    routesScalaSources.builtBy(routesCompile);
                }
            };
        }

        @Override
        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof PlayApplicationBinarySpecInternal;
        }
    }

}
