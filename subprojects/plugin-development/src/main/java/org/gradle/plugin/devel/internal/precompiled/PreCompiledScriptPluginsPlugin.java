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
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin;

import java.util.List;
import java.util.stream.Collectors;

class PreCompiledScriptPluginsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(GroovyBasePlugin.class);
        project.getPluginManager().apply(JavaGradlePluginPlugin.class);

        project.afterEvaluate(this::exposeScriptsAsPlugins);
    }

    private void declarePluginMetadata(GradlePluginDevelopmentExtension pluginExtension, List<PreCompiledScript> scriptPlugins) {
        pluginExtension.plugins(pluginDeclarations ->
            scriptPlugins.forEach(scriptPlugin ->
                pluginDeclarations.create(scriptPlugin.getId(), scriptPlugin::declarePlugin)));
    }

    private void exposeScriptsAsPlugins(Project project) {
        GradlePluginDevelopmentExtension pluginExtension = project.getExtensions().getByType(GradlePluginDevelopmentExtension.class);

        SourceSet pluginSourceSet = pluginExtension.getPluginSourceSet();
        FileTree scriptPluginFiles = pluginSourceSet.getAllSource().matching(PreCompiledScript::filterPluginFiles);
        if (scriptPluginFiles.isEmpty()) {
            return;
        }

        List<PreCompiledScript> scriptPlugins = scriptPluginFiles.getFiles().stream()
            .map(PreCompiledScript::new)
            .collect(Collectors.toList());

        declarePluginMetadata(pluginExtension, scriptPlugins);

        TaskProvider<PreCompileGroovyScriptsTask> preCompileTask = project.getTasks().register(
            "preCompileScriptPlugins", PreCompileGroovyScriptsTask.class, scriptPluginFiles.getFiles(), scriptPlugins);

        pluginSourceSet.getOutput().dir(preCompileTask.flatMap(PreCompileGroovyScriptsTask::getPrecompiledGroovyScriptsDir));
        pluginSourceSet.getJava().srcDir(preCompileTask.flatMap(PreCompileGroovyScriptsTask::getGeneratedPluginAdaptersDir));
    }
}
