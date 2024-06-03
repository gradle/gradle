/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.plugins.quality.internal;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Callables;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.plugins.quality.CodeQualityExtension;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.internal.Cast;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;

public abstract class AbstractCodeQualityPlugin<T> implements Plugin<ProjectInternal> {
    protected static ConventionMapping conventionMappingOf(Object object) {
        return ((IConventionAware) object).getConventionMapping();
    }

    protected ProjectInternal project;
    protected CodeQualityExtension extension;

    @Override
    public final void apply(ProjectInternal project) {
        this.project = project;

        beforeApply();
        project.getPluginManager().apply(ReportingBasePlugin.class);
        createConfigurations();
        extension = createExtension();
        configureExtensionRule();
        configureTaskRule();
        configureSourceSetRule();
        configureCheckTask();
    }

    protected abstract String getToolName();

    protected abstract Class<T> getTaskType();

    private Class<? extends Task> getCastedTaskType() {
        return Cast.uncheckedCast(getTaskType());
    }

    protected String getTaskBaseName() {
        return getToolName().toLowerCase();
    }

    protected String getConfigurationName() {
        return getToolName().toLowerCase();
    }

    protected String getReportName() {
        return getToolName().toLowerCase();
    }

    protected Directory getRootProjectDirectory() {
        return project.getIsolated().getRootProject().getProjectDirectory();
    }

    @SuppressWarnings("rawtypes")
    protected Class<? extends Plugin> getBasePlugin() {
        return JavaBasePlugin.class;
    }

    protected void beforeApply() {
    }

    @SuppressWarnings("deprecation")
    protected void createConfigurations() {
        Configuration configuration = project.getConfigurations().resolvableDependencyScopeUnlocked(getConfigurationName());
        configuration.setVisible(false);
        configuration.setTransitive(true);
        configuration.setDescription("The " + getToolName() + " libraries to be used for this project.");
        getJvmPluginServices().configureAsRuntimeClasspath(configuration);

        // Don't need these things, they're provided by the runtime
        configuration.exclude(excludeProperties("ant", "ant"));
        configuration.exclude(excludeProperties("org.apache.ant", "ant"));
        configuration.exclude(excludeProperties("org.apache.ant", "ant-launcher"));
        configuration.exclude(excludeProperties("org.slf4j", "slf4j-api"));
        configuration.exclude(excludeProperties("org.slf4j", "jcl-over-slf4j"));
        configuration.exclude(excludeProperties("org.slf4j", "log4j-over-slf4j"));
        configuration.exclude(excludeProperties("commons-logging", "commons-logging"));
        configuration.exclude(excludeProperties("log4j", "log4j"));
        configureConfiguration(configuration);
    }

    protected abstract void configureConfiguration(Configuration configuration);

    private Map<String, String> excludeProperties(String group, String module) {
        return ImmutableMap.<String, String>builder()
            .put("group", group)
            .put("module", module)
            .build();
    }

    protected abstract CodeQualityExtension createExtension();

    @SuppressWarnings("rawtypes")
    private void configureExtensionRule() {
        final ConventionMapping extensionMapping = conventionMappingOf(extension);
        extensionMapping.map("sourceSets", Callables.returning(new ArrayList<>()));
        extensionMapping.map("reportsDir", new Callable<File>() {
            @Override
            public File call() {
                return project.getExtensions().getByType(ReportingExtension.class).file(getReportName());
            }
        });
        withBasePlugin(new Action<Plugin>() {
            @Override
            public void execute(Plugin plugin) {
                extensionMapping.map("sourceSets", new Callable<SourceSetContainer>() {
                    @Override
                    public SourceSetContainer call() {
                        return getJavaPluginExtension().getSourceSets();
                    }
                });
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void configureTaskRule() {
        project.getTasks().withType(getCastedTaskType()).configureEach(new Action<Task>() {
            @Override
            public void execute(Task task) {
                String prunedName = task.getName().replaceFirst(getTaskBaseName(), "");
                if (prunedName.isEmpty()) {
                    prunedName = task.getName();
                }
                prunedName = ("" + prunedName.charAt(0)).toLowerCase() + prunedName.substring(1);
                configureTaskDefaults((T) task, prunedName);
            }
        });
    }

    protected void configureTaskDefaults(T task, String baseName) {
    }

    @SuppressWarnings("rawtypes")
    private void configureSourceSetRule() {
        withBasePlugin(new Action<Plugin>() {
            @Override
            public void execute(Plugin plugin) {
                configureForSourceSets(getJavaPluginExtension().getSourceSets());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void configureForSourceSets(SourceSetContainer sourceSets) {
        sourceSets.all(new Action<SourceSet>() {
            @Override
            public void execute(final SourceSet sourceSet) {
                project.getTasks().register(sourceSet.getTaskName(getTaskBaseName(), null), getCastedTaskType(), new Action<Task>() {
                    @Override
                    public void execute(Task task) {
                        configureForSourceSet(sourceSet, (T) task);
                    }
                });
            }
        });
    }

    protected void configureForSourceSet(SourceSet sourceSet, T task) {
    }

    @SuppressWarnings("rawtypes")
    private void configureCheckTask() {
        withBasePlugin(new Action<Plugin>() {
            @Override
            public void execute(Plugin plugin) {
                configureCheckTaskDependents();
            }
        });
    }

    private void configureCheckTaskDependents() {
        final String taskBaseName = getTaskBaseName();
        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME, new Action<Task>() {
            @Override
            public void execute(Task task) {
                task.dependsOn(new Callable<Object>() {
                    @Override
                    public Object call() {
                        return Iterables.transform(extension.getSourceSets(), new Function<SourceSet, String>() {
                            @Override
                            public String apply(SourceSet sourceSet) {
                                return sourceSet.getTaskName(taskBaseName, null);
                            }
                        });
                    }
                });
            }
        });
    }

    @SuppressWarnings("rawtypes")
    protected void withBasePlugin(Action<Plugin> action) {
        project.getPlugins().withType(getBasePlugin(), action);
    }

    protected JavaPluginExtension getJavaPluginExtension() {
        return project.getExtensions().getByType(JavaPluginExtension.class);
    }

    @Inject
    protected abstract JvmPluginServices getJvmPluginServices();
}
