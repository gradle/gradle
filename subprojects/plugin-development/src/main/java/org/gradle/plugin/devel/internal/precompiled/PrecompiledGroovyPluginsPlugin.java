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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.tasks.GroovySourceDirectorySet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.resource.TextFileResourceLoader;
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension;
import org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

import static org.gradle.api.internal.plugins.DefaultPluginManager.CORE_PLUGIN_NAMESPACE;
import static org.gradle.api.internal.plugins.DefaultPluginManager.CORE_PLUGIN_PREFIX;

public abstract class PrecompiledGroovyPluginsPlugin implements Plugin<Project> {

    private static final Documentation PRECOMPILED_SCRIPT_MANUAL = Documentation.userManual("custom_plugins", "sec:precompiled_plugins");

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(GroovyBasePlugin.class);
        project.getPluginManager().apply(JavaGradlePluginPlugin.class);

        project.afterEvaluate(this::exposeScriptsAsPlugins);
    }

    @Inject
    protected abstract TextFileResourceLoader getTextFileResourceLoader();

    private void exposeScriptsAsPlugins(Project project) {
        GradlePluginDevelopmentExtension pluginExtension = project.getExtensions().getByType(GradlePluginDevelopmentExtension.class);

        SourceSet pluginSourceSet = pluginExtension.getPluginSourceSet();
        FileTree scriptPluginFiles = pluginSourceSet.getAllSource().matching(PrecompiledGroovyScript::filterPluginFiles);

        List<PrecompiledGroovyScript> scriptPlugins = scriptPluginFiles.getFiles().stream()
            .map(file -> new PrecompiledGroovyScript(file, getTextFileResourceLoader()))
            .peek(scriptPlugin -> validateScriptPlugin(project, scriptPlugin))
            .collect(Collectors.toList());

        declarePluginMetadata(pluginExtension, scriptPlugins);

        DirectoryProperty buildDir = project.getLayout().getBuildDirectory();
        TaskContainer tasks = project.getTasks();

        TaskProvider<ExtractPluginRequestsTask> extractPluginRequests = tasks.register("extractPluginRequests", ExtractPluginRequestsTask.class, task -> {
            task.getScriptPlugins().convention(scriptPlugins);
            task.getScriptFiles().from(scriptPluginFiles.getFiles());
            task.getExtractedPluginRequestsClassesDirectory().convention(buildDir.dir("groovy-dsl-plugins/output/plugin-requests"));
            task.getExtractedPluginRequestsClassesStagingDirectory().convention(buildDir.dir("groovy-dsl-plugins/output/plugin-requests-staging"));
        });

        TaskProvider<GeneratePluginAdaptersTask> generatePluginAdapters = tasks.register("generatePluginAdapters", GeneratePluginAdaptersTask.class, task -> {
            task.getScriptPlugins().convention(scriptPlugins);
            task.getExtractedPluginRequestsClassesDirectory().convention(extractPluginRequests.flatMap(ExtractPluginRequestsTask::getExtractedPluginRequestsClassesDirectory));
            task.getPluginAdapterSourcesOutputDirectory().convention(buildDir.dir("groovy-dsl-plugins/output/adapter-src"));
        });

        TaskProvider<CompileGroovyScriptPluginsTask> precompilePlugins = tasks.register("compileGroovyPlugins", CompileGroovyScriptPluginsTask.class, task -> {
            task.getScriptPlugins().convention(scriptPlugins);
            task.getScriptFiles().from(scriptPluginFiles.getFiles());
            task.getPrecompiledGroovyScriptsOutputDirectory().convention(buildDir.dir("groovy-dsl-plugins/output/plugin-classes"));

            SourceDirectorySet javaSource = pluginSourceSet.getJava();
            SourceDirectorySet groovySource = pluginSourceSet.getExtensions().getByType(GroovySourceDirectorySet.class);
            task.getClasspath().from(pluginSourceSet.getCompileClasspath(), javaSource.getClassesDirectory(), groovySource.getClassesDirectory());
        });

        pluginSourceSet.getJava().srcDir(generatePluginAdapters.flatMap(GeneratePluginAdaptersTask::getPluginAdapterSourcesOutputDirectory));
        pluginSourceSet.getOutput().dir(precompilePlugins.flatMap(CompileGroovyScriptPluginsTask::getPrecompiledGroovyScriptsOutputDirectory));
        pluginSourceSet.getOutput().dir(extractPluginRequests.flatMap(ExtractPluginRequestsTask::getExtractedPluginRequestsClassesStagingDirectory));
    }

    private void validateScriptPlugin(Project project, PrecompiledGroovyScript scriptPlugin) {
        if (scriptPlugin.getId().equals(CORE_PLUGIN_NAMESPACE) || scriptPlugin.getId().startsWith(CORE_PLUGIN_PREFIX)) {
            throw new PrecompiledScriptException(
                String.format("The precompiled plugin (%s) cannot start with '%s'.", project.relativePath(scriptPlugin.getFileName()), CORE_PLUGIN_NAMESPACE),
                PRECOMPILED_SCRIPT_MANUAL.getConsultDocumentationMessage());
        }
        Plugin<?> existingPlugin = project.getPlugins().findPlugin(scriptPlugin.getId());
        if (existingPlugin != null && existingPlugin.getClass().getPackage().getName().startsWith(CORE_PLUGIN_PREFIX)) {
            throw new PrecompiledScriptException(
                String.format("The precompiled plugin (%s) conflicts with the core plugin '%s'. Rename your plugin.", project.relativePath(scriptPlugin.getFileName()), scriptPlugin.getId()),
                PRECOMPILED_SCRIPT_MANUAL.getConsultDocumentationMessage());
        }
    }

    private void declarePluginMetadata(GradlePluginDevelopmentExtension pluginExtension, List<PrecompiledGroovyScript> scriptPlugins) {
        pluginExtension.plugins(pluginDeclarations ->
            scriptPlugins.forEach(scriptPlugin -> pluginDeclarations.create(scriptPlugin.getId(), scriptPlugin::declarePlugin)));
    }
}
