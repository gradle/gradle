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
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin;

import java.util.List;
import java.util.stream.Collectors;

class PrecompiledGroovyPluginsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(GroovyBasePlugin.class);
        project.getPluginManager().apply(JavaGradlePluginPlugin.class);

        project.afterEvaluate(this::exposeScriptsAsPlugins);
    }

    private void declarePluginMetadata(GradlePluginDevelopmentExtension pluginExtension, List<PrecompiledGroovyScript> scriptPlugins) {
        pluginExtension.plugins(pluginDeclarations ->
            scriptPlugins.forEach(scriptPlugin ->
                pluginDeclarations.create(scriptPlugin.getId(), scriptPlugin::declarePlugin)));
    }

    private void exposeScriptsAsPlugins(Project project) {
        GradlePluginDevelopmentExtension pluginExtension = project.getExtensions().getByType(GradlePluginDevelopmentExtension.class);

        SourceSet pluginSourceSet = pluginExtension.getPluginSourceSet();
        FileTree scriptPluginFiles = pluginSourceSet.getAllSource().matching(PrecompiledGroovyScript::filterPluginFiles);
        if (scriptPluginFiles.isEmpty()) {
            return;
        }

        List<PrecompiledGroovyScript> scriptPlugins = scriptPluginFiles.getFiles().stream()
            .map(PrecompiledGroovyScript::new)
            .collect(Collectors.toList());

        declarePluginMetadata(pluginExtension, scriptPlugins);

        TaskContainer tasks = project.getTasks();

        Provider<Directory> javaClasses = tasks.named("compileJava", AbstractCompile.class).flatMap(AbstractCompile::getDestinationDirectory);
        Provider<Directory> groovyClasses = tasks.named("compileGroovy", AbstractCompile.class).flatMap(AbstractCompile::getDestinationDirectory);

        TaskProvider<PrecompileGroovyScriptsTask> precompileTask = tasks.register(
            "compileGroovyPlugins", PrecompileGroovyScriptsTask.class, scriptPluginFiles.getFiles(), scriptPlugins,
            pluginSourceSet.getCompileClasspath(),
            javaClasses,
            groovyClasses
        );

        TaskProvider<JavaCompile> compilePluginAdapters = tasks.register("compilePluginAdapters", JavaCompile.class, t -> {
            t.dependsOn(precompileTask);
            t.setSource(precompileTask.flatMap(PrecompileGroovyScriptsTask::getPluginAdapterSourcesOutputDir));
            t.setDestinationDir(precompileTask.flatMap(PrecompileGroovyScriptsTask::getAdapterClassesOutputDir));
            t.setClasspath(pluginSourceSet.getCompileClasspath());
        });

        tasks.named("pluginDescriptors").configure(t -> t.dependsOn(compilePluginAdapters));

        pluginSourceSet.getOutput().dir(precompileTask.flatMap(PrecompileGroovyScriptsTask::getPrecompiledGroovyScriptsOutputDir));
        pluginSourceSet.getOutput().dir(compilePluginAdapters.flatMap(JavaCompile::getDestinationDirectory));
    }
}
