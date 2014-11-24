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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageRegistration;
import org.gradle.language.base.internal.LanguageRegistry;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.coffeescript.CoffeeScriptSourceSet;
import org.gradle.language.coffeescript.internal.CoffeeScriptSourceSetInternal;
import org.gradle.language.coffeescript.internal.DefaultCoffeeScriptSourceSet;
import org.gradle.language.javascript.JavaScriptSourceSet;
import org.gradle.language.javascript.internal.DefaultJavaScriptSourceSet;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpecContainer;
import org.gradle.platform.base.TransformationFileType;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.play.JavaScriptFile;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.tasks.CoffeeScriptCompile;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Plugin for adding coffeescript compilation to a Play application
 */
public class PlayCoffeeScriptPlugin implements Plugin<Project> {

    public void apply(Project project) {
        project.apply(Collections.singletonMap("plugin", PlayApplicationPlugin.class));
    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    static class Rules {
        @Mutate
        void registerLanguage(LanguageRegistry languages) {
            languages.add(new CoffeeScript());
        }

        @Mutate
        void createCoffeeScriptSources(ComponentSpecContainer components, final ServiceRegistry serviceRegistry) {
            components.withType(PlayApplicationSpec.class).all(new Action<PlayApplicationSpec>() {
                public void execute(PlayApplicationSpec playComponent) {
                    CoffeeScriptSourceSet coffeeScriptSourceSet =
                            new DefaultCoffeeScriptSourceSet("coffeeScriptSources", playComponent.getName(), serviceRegistry.get(FileResolver.class));
                    coffeeScriptSourceSet.getSource().srcDir("app");
                    coffeeScriptSourceSet.getSource().include("**/*.coffee");
                    ((ComponentSpecInternal) playComponent).getSources().add(coffeeScriptSourceSet);
                    ((ComponentSpecInternal) playComponent).getSources().add(((CoffeeScriptSourceSetInternal)coffeeScriptSourceSet).getOutputSourceSet());
                }
            });
        }
    }

    /**
     * CoffeeScript language implementation
     */
    static class CoffeeScript implements LanguageRegistration<CoffeeScriptSourceSet> {
        public String getName() {
            return "coffeescript";
        }

        public Class<CoffeeScriptSourceSet> getSourceSetType() {
            return CoffeeScriptSourceSet.class;
        }

        public Class<? extends CoffeeScriptSourceSet> getSourceSetImplementation() {
            return DefaultCoffeeScriptSourceSet.class;
        }

        public Map<String, Class<?>> getBinaryTools() {
            return Collections.emptyMap();
        }

        public Class<? extends TransformationFileType> getOutputType() {
            return JavaScriptFile.class;
        }

        public SourceTransformTaskConfig getTransformTask() {
            return new SourceTransformTaskConfig() {
                public String getTaskPrefix() {
                    return "compile";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return CoffeeScriptCompile.class;
                }

                public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
                    CoffeeScriptCompile coffeeScriptCompile = (CoffeeScriptCompile) task;
                    PlayApplicationBinarySpec playBinarySpec = (PlayApplicationBinarySpec) binary;
                    coffeeScriptCompile.setDescription(String.format("Compiles the %s of %s", sourceSet, binary));
                    File coffeeScriptCompileOutputDirectory = new File(task.getProject().getBuildDir(), String.format("%s/coffeescript", binary.getName()));
                    coffeeScriptCompile.setOutputDirectory(coffeeScriptCompileOutputDirectory);
                    coffeeScriptCompile.setPlatform(playBinarySpec.getTargetPlatform());
                    coffeeScriptCompile.setSource(sourceSet.getSource());

                    LanguageSourceSet outputSourceSet = ((CoffeeScriptSourceSetInternal)sourceSet).getOutputSourceSet();
                    outputSourceSet.getSource().srcDir(coffeeScriptCompileOutputDirectory);
                    outputSourceSet.builtBy(coffeeScriptCompile);
                }
            };
        }

        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof PlayApplicationBinarySpec;
        }
    }
}
