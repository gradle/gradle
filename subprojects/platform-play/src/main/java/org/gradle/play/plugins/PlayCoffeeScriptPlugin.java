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

package org.gradle.play.plugins;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.coffeescript.CoffeeScriptSourceSet;
import org.gradle.language.javascript.JavaScriptSourceSet;
import org.gradle.model.Defaults;
import org.gradle.model.Each;
import org.gradle.model.Finalize;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.internal.JavaScriptSourceCode;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.tasks.PlayCoffeeScriptCompile;
import org.gradle.util.SingleMessageLogger;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Plugin for adding coffeescript compilation to a Play application.  Adds support for
 * defining {@link org.gradle.language.coffeescript.CoffeeScriptSourceSet} source sets.  A
 * "coffeeScript" source set is created by default.
 */
@SuppressWarnings("UnusedDeclaration")
@Incubating
@Deprecated
public class PlayCoffeeScriptPlugin implements Plugin<Project> {
    private static final String DEFAULT_COFFEESCRIPT_VERSION = "1.8.0";
    private static final String DEFAULT_RHINO_VERSION = "1.7R4";

    static String getDefaultCoffeeScriptDependencyNotation() {
        return "org.coffeescript:coffee-script-js:" + DEFAULT_COFFEESCRIPT_VERSION + "@js";
    }

    static String getDefaultRhinoDependencyNotation() {
        return "org.mozilla:rhino:" + DEFAULT_RHINO_VERSION;
    }

    @Override
    public void apply(Project target) {
        SingleMessageLogger.nagUserOfPluginReplacedWithExternalOne("Play CoffeeScript", "org.gradle.playframework");
        target.getPluginManager().apply(ComponentModelBasePlugin.class);
    }

    static class Rules extends RuleSource {
        @ComponentType
        void registerCoffeeScript(TypeBuilder<CoffeeScriptSourceSet> builder) {
        }

        @Finalize
        void createCoffeeScriptSourceSets(@Each PlayApplicationSpec playComponent) {
            playComponent.getSources().create("coffeeScript", CoffeeScriptSourceSet.class, new Action<CoffeeScriptSourceSet>() {
                @Override
                public void execute(CoffeeScriptSourceSet coffeeScriptSourceSet) {
                    coffeeScriptSourceSet.getSource().srcDir("app/assets");
                    coffeeScriptSourceSet.getSource().include("**/*.coffee");
                }
            });
        }

        @Mutate
        void createGeneratedJavaScriptSourceSets(@Path("binaries") ModelMap<PlayApplicationBinarySpecInternal> binaries, final ObjectFactory objectFactory) {
            binaries.all(new Action<PlayApplicationBinarySpecInternal>() {
                @Override
                public void execute(PlayApplicationBinarySpecInternal playApplicationBinarySpec) {
                    for (CoffeeScriptSourceSet coffeeScriptSourceSet : playApplicationBinarySpec.getInputs().withType(CoffeeScriptSourceSet.class)) {
                        playApplicationBinarySpec.addGeneratedJavaScript(coffeeScriptSourceSet, objectFactory);
                    }
                }
            });
        }

        @Defaults
        void configureCoffeeScriptCompileDefaults(@Each PlayCoffeeScriptCompile coffeeScriptCompile) {
            coffeeScriptCompile.setRhinoClasspathNotation(getDefaultRhinoDependencyNotation());
            coffeeScriptCompile.setCoffeeScriptJsNotation(getDefaultCoffeeScriptDependencyNotation());
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages) {
            languages.add(new CoffeeScript());
        }
    }

    private static class CoffeeScript implements LanguageTransform<CoffeeScriptSourceSet, JavaScriptSourceCode> {
        @Override
        public String getLanguageName() {
            return "coffeeScript";
        }

        @Override
        public Class<CoffeeScriptSourceSet> getSourceSetType() {
            return CoffeeScriptSourceSet.class;
        }

        @Override
        public Class<JavaScriptSourceCode> getOutputType() {
            return JavaScriptSourceCode.class;
        }

        @Override
        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        @Override
        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                @Override
                public String getTaskPrefix() {
                    return "compile";
                }

                @Override
                public Class<? extends DefaultTask> getTaskType() {
                    return PlayCoffeeScriptCompile.class;
                }

                @Override
                public void configureTask(Task task, BinarySpec binarySpec, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
                    PlayApplicationBinarySpecInternal binary = (PlayApplicationBinarySpecInternal) binarySpec;
                    CoffeeScriptSourceSet coffeeScriptSourceSet = (CoffeeScriptSourceSet) sourceSet;
                    PlayCoffeeScriptCompile coffeeScriptCompile = (PlayCoffeeScriptCompile) task;
                    JavaScriptSourceSet javaScriptSourceSet = binary.getGeneratedJavaScript().get(coffeeScriptSourceSet);

                    coffeeScriptCompile.setDescription("Compiles coffeescript for the " + coffeeScriptSourceSet.getDisplayName() + ".");

                    File generatedSourceDir = binary.getNamingScheme().getOutputDirectory(task.getProject().getBuildDir(), "src");
                    File outputDirectory = new File(generatedSourceDir, javaScriptSourceSet.getName());
                    coffeeScriptCompile.setDestinationDir(outputDirectory);
                    coffeeScriptCompile.setSource(coffeeScriptSourceSet.getSource());

                    javaScriptSourceSet.getSource().srcDir(outputDirectory);
                    javaScriptSourceSet.builtBy(coffeeScriptCompile);
                }
            };
        }

        @Override
        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof PlayApplicationBinarySpecInternal;
        }
    }
}
