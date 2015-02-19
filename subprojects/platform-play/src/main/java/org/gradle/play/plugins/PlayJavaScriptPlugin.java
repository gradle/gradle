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
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.language.javascript.JavaScriptSourceSet;
import org.gradle.language.javascript.internal.DefaultJavaScriptSourceSet;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.platform.base.internal.toolchain.ResolvedTool;
import org.gradle.platform.base.internal.toolchain.ToolResolver;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.PlayApplicationSpec;
import org.gradle.play.internal.javascript.JavaScriptCompileSpec;
import org.gradle.play.tasks.JavaScriptMinify;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Plugin for adding javascript processing to a Play application.  Registers "javascript" language support with the {@link org.gradle.language.javascript.JavaScriptSourceSet}.
 */
@SuppressWarnings("UnusedDeclaration")
@Incubating
public class PlayJavaScriptPlugin extends RuleSource {
    @LanguageType
    void registerJavascript(LanguageTypeBuilder<JavaScriptSourceSet> builder) {
        builder.setLanguageName("javaScript");
        builder.defaultImplementation(DefaultJavaScriptSourceSet.class);
    }

    @Mutate
    void createJavascriptSourceSets(CollectionBuilder<PlayApplicationSpec> components) {
        components.beforeEach(new Action<PlayApplicationSpec>() {
            @Override
            public void execute(PlayApplicationSpec playComponent) {
                // TODO - should have some way to lookup using internal type
                JavaScriptSourceSet javaScriptSourceSet = ((ComponentSpecInternal) playComponent).getSources().create("javaScriptAssets", JavaScriptSourceSet.class);
                javaScriptSourceSet.getSource().srcDir("app/assets");
                javaScriptSourceSet.getSource().include("**/*.js");
            }
        });
    }

    @BinaryTasks
    void createJavaScriptTasks(CollectionBuilder<Task> tasks, final PlayApplicationBinarySpec binary, ServiceRegistry serviceRegistry, @Path("buildDir") final File buildDir) {
        ToolResolver toolResolver = serviceRegistry.get(ToolResolver.class);
        ResolvedTool<Compiler<JavaScriptCompileSpec>> compilerTool = toolResolver.resolveCompiler(JavaScriptCompileSpec.class, binary.getTargetPlatform());
        for (JavaScriptSourceSet javaScriptSourceSet : binary.getSource().withType(JavaScriptSourceSet.class)) {
            if (((LanguageSourceSetInternal) javaScriptSourceSet).getMayHaveSources()) {
                createJavaScriptMinifyTask(tasks, javaScriptSourceSet, binary, compilerTool, buildDir);
            }
        }

        for (JavaScriptSourceSet javaScriptSourceSet : binary.getGeneratedJavaScript().values()) {
            createJavaScriptMinifyTask(tasks, javaScriptSourceSet, binary, compilerTool, buildDir);
        }
    }

    void createJavaScriptMinifyTask(CollectionBuilder<Task> tasks, final JavaScriptSourceSet javaScriptSourceSet, final PlayApplicationBinarySpec binary, final ResolvedTool<Compiler<JavaScriptCompileSpec>> compilerTool,  @Path("buildDir") final File buildDir) {
        final String minifyTaskName = "minify" + capitalize(binary.getName()) + capitalize(javaScriptSourceSet.getName());
        final File minifyOutputDirectory = new File(buildDir, String.format("%s/src/%s", binary.getName(), minifyTaskName));
        tasks.create(minifyTaskName, JavaScriptMinify.class, new Action<JavaScriptMinify>() {
            @Override
            public void execute(JavaScriptMinify javaScriptMinify) {
                javaScriptMinify.setSource(javaScriptSourceSet.getSource());
                javaScriptMinify.setDestinationDir(minifyOutputDirectory);
                javaScriptMinify.setCompilerTool(compilerTool);

                binary.getAssets().builtBy(javaScriptMinify);
                binary.getAssets().addAssetDir(minifyOutputDirectory);

                javaScriptMinify.dependsOn(javaScriptSourceSet.getBuildDependencies());
            }
        });
    }
}
