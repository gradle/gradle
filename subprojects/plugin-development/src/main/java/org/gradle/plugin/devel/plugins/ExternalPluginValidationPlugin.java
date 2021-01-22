/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.plugin.devel.plugins;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.initialization.ScriptHandlerInternal;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugin.devel.tasks.ValidatePlugins;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin.PLUGIN_DEVELOPMENT_GROUP;
import static org.gradle.plugin.devel.plugins.JavaGradlePluginPlugin.VALIDATE_PLUGIN_TASK_DESCRIPTION;

/**
 * Validates plugins applied to a build, by checking property annotations on work items like tasks and artifact transforms.
 * This plugin is similar to {@link ValidatePlugins} but instead of checking the plugins <i>written in</i> the current build,
 * it checks the plugins <i>applied to</i> the current build.
 *
 * See the user guide for more information on
 * <a href="https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks" target="_top">incremental build</a> and
 * <a href="https://docs.gradle.org/current/userguide/build_cache.html#sec:task_output_caching" target="_top">caching task outputs</a>.
 *
 * @since 7.0
 */
@Incubating
@CacheableTask
public abstract class ExternalPluginValidationPlugin implements Plugin<Project> {

    private final static String VALIDATE_EXTERNAL_PLUGINS_TASK_NAME = "validateExternalPlugins";
    private final static String VALIDATE_EXTERNAL_PLUGIN_TASK_PREFIX = "validatePluginWithId_";

    private final FileOperations fileOperations;

    @Inject
    public ExternalPluginValidationPlugin(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    @Override
    public void apply(Project project) {
        TaskProvider<Task> lifecycleTask = createLifecycleTask(project);
        PluginRegistry registry = findPluginRegistry(project);
        Map<PluginId, List<File>> jarsByPlugin = Maps.newHashMap();
        project.getPlugins().configureEach(p -> {
            Class<?> pluginClass = p.getClass();
            if (isExternal(pluginClass)) {
                registry.findPluginForClass(pluginClass).ifPresent(pluginId -> {
                    File pluginJar = findPluginJar(pluginClass);
                    if (pluginJar != null) {
                        List<File> pluginJars = jarsByPlugin.get(pluginId);
                        if (pluginJars == null) {
                            pluginJars = Lists.newArrayList();
                            TaskProvider<ValidatePlugins> validationTask = configureValidationTask(project, pluginJars, pluginId);
                            lifecycleTask.configure(t -> t.dependsOn(validationTask));
                            jarsByPlugin.put(pluginId, pluginJars);
                        }
                        pluginJars.add(pluginJar);
                    }
                });
            }
        });
    }

    private static PluginRegistry findPluginRegistry(Project project) {
        return ((ProjectInternal) project).getServices().get(PluginRegistry.class);
    }

    private static TaskProvider<Task> createLifecycleTask(Project project) {
        return project.getTasks().register(VALIDATE_EXTERNAL_PLUGINS_TASK_NAME);
    }

    private TaskProvider<ValidatePlugins> configureValidationTask(Project project,
                                                                         List<File> pluginJars,
                                                                         PluginId pluginId) {
        String idWithoutDots = pluginId.getId().replace('.', '_');
        return project.getTasks().register(VALIDATE_EXTERNAL_PLUGIN_TASK_PREFIX + idWithoutDots, ValidatePlugins.class, task -> {
            task.setGroup(PLUGIN_DEVELOPMENT_GROUP);
            task.setDescription(VALIDATE_PLUGIN_TASK_DESCRIPTION);
            task.getOutputFile().set(project.getLayout().getBuildDirectory().file("reports/plugins/validation-report-for-" + idWithoutDots + ".txt"));

            ScriptHandlerInternal scriptHandler = (ScriptHandlerInternal) project.getBuildscript();
            List<File> scriptClassPath = scriptHandler.getScriptClassPath().getAsFiles();
            task.getClasses().setFrom(pluginClassesOf(pluginJars));
            task.getClasspath().setFrom(scriptClassPath);
        });
    }

    private List<FileTree> pluginClassesOf(List<File> pluginJars) {
        return pluginJars.stream()
            .map(fileOperations::zipTree)
            .collect(Collectors.toList());
    }

    @Nullable
    private static File findPluginJar(Class<?> pluginClass) {
        return toSourceFile(pluginClass.getProtectionDomain().getCodeSource().getLocation());
    }

    private static boolean isExternal(Class<?> pluginClass) {
        return !pluginClass.getName().startsWith("org.gradle");
    }

    @Nullable
    private static File toSourceFile(URL url) {
        try {
            return new File(url.toURI());
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
