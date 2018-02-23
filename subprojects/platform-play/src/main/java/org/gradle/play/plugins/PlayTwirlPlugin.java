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
import org.gradle.api.specs.Spec;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.scala.ScalaLanguageSourceSet;
import org.gradle.language.twirl.TwirlImports;
import org.gradle.language.twirl.TwirlSourceSet;
import org.gradle.language.twirl.internal.DefaultTwirlSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.platform.base.internal.PlatformResolvers;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.internal.ScalaSourceCode;
import org.gradle.play.internal.platform.PlayPlatformInternal;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.tasks.TwirlCompile;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Plugin for compiling Twirl sources in a Play application.
 */
@SuppressWarnings("UnusedDeclaration")
@Incubating
public class PlayTwirlPlugin extends RuleSource {

    @ComponentType
    void registerTwirlLanguageType(TypeBuilder<TwirlSourceSet> builder) {
        builder.defaultImplementation(DefaultTwirlSourceSet.class);
    }

    @Mutate
    void createGeneratedScalaSourceSets(@Path("binaries") ModelMap<PlayApplicationBinarySpecInternal> binaries, final SourceDirectorySetFactory sourceDirectorySetFactory) {
        binaries.all(new Action<PlayApplicationBinarySpecInternal>() {
            @Override
            public void execute(PlayApplicationBinarySpecInternal playApplicationBinarySpec) {
                for (LanguageSourceSet languageSourceSet : playApplicationBinarySpec.getInputs().withType(TwirlSourceSet.class)) {
                    playApplicationBinarySpec.addGeneratedScala(languageSourceSet, sourceDirectorySetFactory);
                }
            }
        });
    }

    @Mutate
    // TODO:LPTR This should be @Defaults @Each PlayApplicationBinarySpecInternal
    void addPlayJavaDependencyIfNeeded(@Path("binaries") ModelMap<PlayApplicationBinarySpecInternal> binaries, final PlayPluginConfigurations configurations, final PlatformResolvers platforms) {
        binaries.beforeEach(new Action<PlayApplicationBinarySpecInternal>() {
            @Override
            public void execute(PlayApplicationBinarySpecInternal binary) {
                if (hasTwirlSourceSetsWithJavaImports(binary.getApplication())) {
                    PlayPlatform targetPlatform = binary.getTargetPlatform();
                    configurations.getPlay().addDependency(((PlayPlatformInternal) targetPlatform).getDependencyNotation("play-java"));
                }
            }
        });
    }

    @Mutate
    void registerLanguageTransform(LanguageTransformContainer languages) {
        languages.add(new Twirl());
    }

    private static boolean hasTwirlSourceSetsWithJavaImports(PlayApplicationSpec playApplicationSpec) {
        return CollectionUtils.any(playApplicationSpec.getSources().withType(TwirlSourceSet.class).values(), new Spec<TwirlSourceSet>() {
            @Override
            public boolean isSatisfiedBy(TwirlSourceSet twirlSourceSet) {
                return twirlSourceSet.getDefaultImports() == TwirlImports.JAVA;
            }
        });
    }

    private static class Twirl implements LanguageTransform<TwirlSourceSet, ScalaSourceCode> {
        @Override
        public String getLanguageName() {
            return "twirl";
        }

        @Override
        public Class<TwirlSourceSet> getSourceSetType() {
            return TwirlSourceSet.class;
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
                    return TwirlCompile.class;
                }

                public void configureTask(Task task, BinarySpec binarySpec, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
                    PlayApplicationBinarySpecInternal binary = (PlayApplicationBinarySpecInternal) binarySpec;
                    TwirlSourceSet twirlSourceSet = (TwirlSourceSet) sourceSet;
                    TwirlCompile twirlCompile = (TwirlCompile) task;
                    ScalaLanguageSourceSet twirlScalaSources = binary.getGeneratedScala().get(twirlSourceSet);

                    File generatedSourceDir = binary.getNamingScheme().getOutputDirectory(task.getProject().getBuildDir(), "src");
                    File twirlCompileOutputDirectory = new File(generatedSourceDir, twirlScalaSources.getName());

                    twirlCompile.setDescription("Compiles Twirl templates for the '" + twirlSourceSet.getName() + "' source set.");
                    twirlCompile.setPlatform(binary.getTargetPlatform());
                    twirlCompile.setSource(twirlSourceSet.getSource());
                    twirlCompile.setOutputDirectory(twirlCompileOutputDirectory);
                    twirlCompile.setDefaultImports(twirlSourceSet.getDefaultImports());
                    twirlCompile.setUserTemplateFormats(twirlSourceSet.getUserTemplateFormats());
                    twirlCompile.setAdditionalImports(twirlSourceSet.getAdditionalImports());

                    twirlScalaSources.getSource().srcDir(twirlCompileOutputDirectory);
                    twirlScalaSources.builtBy(twirlCompile);
                }
            };
        }

        @Override
        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof PlayApplicationBinarySpecInternal;
        }
    }
}
