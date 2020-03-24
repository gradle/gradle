/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.plugin.devel.internal.precompiled;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin;

import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.plugin.devel.internal.precompiled.PreCompiledScript.SCRIPT_PLUGIN_EXTENSION;

class PreCompiledScriptPluginsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaGradlePluginPlugin.class);

        GradlePluginDevelopmentExtension pluginExtension = project.getExtensions().getByType(GradlePluginDevelopmentExtension.class);

        project.afterEvaluate(p -> exposeScriptsAsPlugins(pluginExtension, project.getTasks(), project.getLayout()));
    }

    private void declarePluginMetadata(GradlePluginDevelopmentExtension pluginExtension, Set<PreCompiledScript> scriptPlugins) {
        pluginExtension.plugins(pluginDeclarations -> {
            for (PreCompiledScript scriptPlugin : scriptPlugins) {
                pluginDeclarations.create(scriptPlugin.getId(), pluginDeclaration -> {
                    pluginDeclaration.setImplementationClass(scriptPlugin.getGeneratedPluginPackage()
                        .map(packageName -> packageName + '.').orElse("") + scriptPlugin.getGeneratedPluginClassName());
                    pluginDeclaration.setId(scriptPlugin.getId());
                });
            }
        });
    }

    private void exposeScriptsAsPlugins(GradlePluginDevelopmentExtension pluginExtension, TaskContainer tasks, ProjectLayout buildLayout) {
        FileTree scriptPluginFiles = pluginExtension.getPluginSourceSet().getAllSource().matching(patternFilterable -> patternFilterable.include("**/*" + SCRIPT_PLUGIN_EXTENSION));
        if (scriptPluginFiles.isEmpty()) {
            return;
        }

        Set<PreCompiledScript> scriptPlugins = scriptPluginFiles.getFiles().stream()
            .map(PreCompiledScript::new)
            .collect(Collectors.toSet());

        declarePluginMetadata(pluginExtension, scriptPlugins);

        Provider<Directory> baseMetadataDir = buildLayout.getBuildDirectory().dir("groovy-dsl/compiled-scripts/groovy-metadata");
        Provider<Directory> baseClassesDir = buildLayout.getBuildDirectory().dir("groovy-dsl/compiled-scripts/groovy");

        TaskProvider<PreCompileGroovyScriptsTask> preCompileTask = tasks.register("preCompileScriptPlugins", PreCompileGroovyScriptsTask.class, task -> {
            task.getScriptPlugins().addAll(scriptPlugins);
            task.getClassesDir().set(baseClassesDir);
            task.getMetadataDir().set(baseMetadataDir);
        });

        pluginExtension.getPluginSourceSet().getOutput().dir(preCompileTask.flatMap(PreCompileGroovyScriptsTask::getClassOutputDir));
        pluginExtension.getPluginSourceSet().getOutput().dir(preCompileTask.flatMap(PreCompileGroovyScriptsTask::getMetadataDir));

        Provider<Directory> generatedClassesDir = buildLayout.getBuildDirectory().dir("generated-classes/groovy-dsl-plugins/java");
        TaskProvider<GenerateScriptPluginAdaptersTask> generateAdaptersTask = tasks.register("generateScriptPluginAdapters", GenerateScriptPluginAdaptersTask.class, task -> {
            task.getScriptPlugins().addAll(scriptPlugins);
            task.getMetadataDir().set(baseMetadataDir);
            task.getClassesDir().set(baseClassesDir);
            task.getGeneratedClassesDir().set(generatedClassesDir);
        });

        pluginExtension.getPluginSourceSet().getJava().srcDir(generateAdaptersTask.flatMap(GenerateScriptPluginAdaptersTask::getGeneratedClassesDir));
    }
}
