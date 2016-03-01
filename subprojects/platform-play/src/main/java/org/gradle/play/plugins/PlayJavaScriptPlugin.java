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

import org.gradle.api.*;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.SourceTransformTaskConfig;
import org.gradle.language.base.internal.registry.LanguageTransform;
import org.gradle.language.base.internal.registry.LanguageTransformContainer;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.javascript.JavaScriptSourceSet;
import org.gradle.language.javascript.internal.DefaultJavaScriptSourceSet;
import org.gradle.model.Each;
import org.gradle.model.Finalize;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.internal.JavaScriptSourceCode;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;
import org.gradle.play.tasks.JavaScriptMinify;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Plugin for adding javascript processing to a Play application.  Registers "javascript" language support with the {@link org.gradle.language.javascript.JavaScriptSourceSet}.
 */
@SuppressWarnings("UnusedDeclaration")
@Incubating
public class PlayJavaScriptPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ComponentModelBasePlugin.class);
    }

    static class Rules extends RuleSource {
        @ComponentType
        void registerJavascript(TypeBuilder<JavaScriptSourceSet> builder) {
            builder.defaultImplementation(DefaultJavaScriptSourceSet.class);
        }

        @Finalize
        void createJavascriptSourceSets(@Each PlayApplicationSpec playComponent) {
            playComponent.getSources().create("javaScript", JavaScriptSourceSet.class, new Action<JavaScriptSourceSet>() {
                @Override
                public void execute(JavaScriptSourceSet javaScriptSourceSet) {
                    javaScriptSourceSet.getSource().srcDir("app/assets");
                    javaScriptSourceSet.getSource().include("**/*.js");
                }
            });
        }

        @Mutate
        void registerLanguageTransform(LanguageTransformContainer languages) {
            languages.add(new JavaScript());
        }
    }

    private static class JavaScript implements LanguageTransform<JavaScriptSourceSet, JavaScriptSourceCode> {
        @Override
        public String getLanguageName() {
            return "javaScript";
        }

        @Override
        public Class<JavaScriptSourceSet> getSourceSetType() {
            return JavaScriptSourceSet.class;
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
                public String getTaskPrefix() {
                    return "minify";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return JavaScriptMinify.class;
                }

                public void configureTask(Task task, BinarySpec binarySpec, LanguageSourceSet sourceSet, ServiceRegistry serviceRegistry) {
                    PlayApplicationBinarySpecInternal binary = (PlayApplicationBinarySpecInternal) binarySpec;
                    JavaScriptSourceSet javaScriptSourceSet = (JavaScriptSourceSet) sourceSet;
                    JavaScriptMinify javaScriptMinify = (JavaScriptMinify) task;

                    javaScriptMinify.setDescription("Minifies javascript for the " + javaScriptSourceSet.getDisplayName() + ".");

                    File generatedSourceDir = binary.getNamingScheme().getOutputDirectory(task.getProject().getBuildDir(), "src");
                    File outputDirectory = new File(generatedSourceDir, javaScriptMinify.getName());
                    javaScriptMinify.setDestinationDir(outputDirectory);

                    javaScriptMinify.setSource(javaScriptSourceSet.getSource());
                    javaScriptMinify.setPlayPlatform(binary.getTargetPlatform());
                    javaScriptMinify.dependsOn(javaScriptSourceSet);
                    binary.getAssets().addAssetDir(outputDirectory);

                    binary.getAssets().builtBy(javaScriptMinify);
                }
            };
        }

        @Override
        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof PlayApplicationBinarySpecInternal;
        }
    }
}
