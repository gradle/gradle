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
import org.gradle.play.tasks.JavaScriptProcessResources;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Plugin for adding javascript processing to a Play application.  Registers "javascript" language support with the {@link org.gradle.language.javascript.JavaScriptSourceSet}.
 */
public class PlayJavaScriptPlugin implements Plugin<Project> {
    public void apply(Project target) {

    }

    /**
     * Model rules.
     */
    @SuppressWarnings("UnusedDeclaration")
    @RuleSource
    static class Rules {
        @Mutate
        void registerLanguage(LanguageRegistry languages) {
            languages.add(new JavaScript());
        }

        @Mutate
        void createJavaScriptSources(ComponentSpecContainer components, final ServiceRegistry serviceRegistry) {
            for (PlayApplicationSpec playComponent : components.withType(PlayApplicationSpec.class)) {
                JavaScriptSourceSet javaScriptSourceSet = new DefaultJavaScriptSourceSet("javaScriptSources", playComponent.getName(), serviceRegistry.get(FileResolver.class));
                javaScriptSourceSet.getSource().srcDir("app");
                javaScriptSourceSet.getSource().include("**/*.js");
                ((ComponentSpecInternal) playComponent).getSources().add(javaScriptSourceSet);
            }
        }
    }

    /**
     * JavaScript language implementation
     */
    static class JavaScript implements LanguageRegistration<JavaScriptSourceSet> {
        public String getName() {
            return "javascript";
        }

        public Class<JavaScriptSourceSet> getSourceSetType() {
            return JavaScriptSourceSet.class;
        }

        public Class<? extends JavaScriptSourceSet> getSourceSetImplementation() {
            return DefaultJavaScriptSourceSet.class;
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
                    return "process";
                }

                public Class<? extends DefaultTask> getTaskType() {
                    return JavaScriptProcessResources.class;
                }

                public void configureTask(Task task, BinarySpec binary, LanguageSourceSet sourceSet) {
                    JavaScriptSourceSet javaScriptSourceSet = (JavaScriptSourceSet) sourceSet;
                    PlayApplicationBinarySpec spec = (PlayApplicationBinarySpec) binary;
                    JavaScriptProcessResources javaScriptProcessResources = (JavaScriptProcessResources) task;
                    javaScriptProcessResources.from(javaScriptSourceSet.getSource());
                    File javascriptOutputDir = new File(task.getProject().getBuildDir(), String.format("%s/javascript", binary.getName()));
                    javaScriptProcessResources.into(javascriptOutputDir);
                    spec.getClasses().addResourceDir(javascriptOutputDir);
                    spec.getClasses().builtBy(javaScriptProcessResources);
                }
            };
        }

        public boolean applyToBinary(BinarySpec binary) {
            return binary instanceof PlayApplicationBinarySpec;
        }
    }
}
