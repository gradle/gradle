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

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.ReportingBasePlugin;
import org.gradle.api.plugins.jvm.internal.JvmPluginServices;
import org.gradle.api.plugins.quality.CodeQualityExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.reporting.ReportingExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec;
import org.gradle.workers.ForkingWorkerSpec;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Base class for all JVM code quality plugins.
 *
 * @param <T> The type of task used by this plugin to analyze code.
 */
public abstract class AbstractCodeQualityPlugin<T extends Task> implements Plugin<ProjectInternal> {
    private static final String OPEN_MODULES_ARG = "java.prefs/java.util.prefs=ALL-UNNAMED";

    // Dependencies provided by Gradle which should be excluded from the tool classpath
    private static final ImmutableSet<ModuleIdentifier> EXCLUDED_MODULES = ImmutableSet.of(
        DefaultModuleIdentifier.newId("org.apache.ant", "ant"),
        DefaultModuleIdentifier.newId("org.apache.ant", "ant"),
        DefaultModuleIdentifier.newId("org.apache.ant", "ant-launcher"),
        DefaultModuleIdentifier.newId("org.slf4j", "slf4j-api"),
        DefaultModuleIdentifier.newId("org.slf4j", "jcl-over-slf4j"),
        DefaultModuleIdentifier.newId("org.slf4j", "log4j-over-slf4j"),
        DefaultModuleIdentifier.newId("commons-logging", "commons-logging"),
        DefaultModuleIdentifier.newId("log4j", "log4j")
    );

    @Inject protected abstract ProviderFactory getProviders();
    @Inject protected abstract ObjectFactory getObjects();
    @Inject protected abstract JvmPluginServices getJvmPluginServices();
    @Inject protected abstract DependencyFactory getDependencyFactory();
    @Inject protected abstract JavaToolchainService getToolchainService();
    @Inject protected abstract ProjectLayout getProjectLayout();

    @Override
    public final void apply(ProjectInternal project) {
        project.getPluginManager().apply(JavaBasePlugin.class);
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);

        project.getPluginManager().apply(ReportingBasePlugin.class);
        ReportingExtension reporting = project.getExtensions().getByType(ReportingExtension.class);

        CodeQualityExtension extension = createExtension(project);
        ConventionMapping extensionMapping = new DslObject(extension).getConventionMapping();
        extensionMapping.map("sourceSets", () -> new ArrayList<>());
        extensionMapping.map("reportsDir", () -> reporting.file(getToolName().toLowerCase()));
        extensionMapping.map("sourceSets", () -> java.getSourceSets());

        FileCollection toolClasspath = createToolClasspath(project.getConfigurations());
        configureTaskRule(project.getTasks(), toolClasspath, java);

        TaskContainer tasks = project.getTasks();
        configureForSourceSets(tasks, java.getSourceSets());
        configureCheckTaskDependents(tasks, extension);
    }

    protected abstract String getToolName();

    protected abstract Class<T> getTaskType();

    /**
     * Called immediately after applying base plugins.
     */
    protected abstract CodeQualityExtension createExtension(Project project);

    protected abstract Set<Dependency> getToolDependencies();

    private String getTaskBaseName() {
        return getToolName().toLowerCase();
    }

    private String getTaskName(SourceSet sourceSet) {
        return sourceSet.getTaskName(getTaskBaseName(), null);
    }

    private FileCollection createToolClasspath(RoleBasedConfigurationContainerInternal configurations) {
        String configurationName = getToolName().toLowerCase();
        Configuration configuration = configurations.resolvableBucket(configurationName);

        getJvmPluginServices().configureAsRuntimeClasspath(configuration);
        configuration.setVisible(false);
        configuration.setDescription("The " + getToolName() + " libraries to be used for this project.");
        configuration.defaultDependencies(deps -> deps.addAll(getToolDependencies()));

        // Don't need these things, they're provided by the runtime
        return configuration.getIncoming().artifactView(view ->
            view.componentFilter(id ->
                !(id instanceof ModuleComponentIdentifier) ||
                !EXCLUDED_MODULES.contains(((ModuleComponentIdentifier) id).getModuleIdentifier())
            )
        ).getFiles();
    }

    private void configureTaskRule(TaskContainer tasks, FileCollection toolClasspath, JavaPluginExtension java) {
        tasks.withType(getTaskType()).configureEach(task -> {
            String baseName = task.getName().replaceFirst(getTaskBaseName(), "");
            baseName = baseName.isEmpty() ? task.getName() : StringUtils.uncapitalize(baseName);

            // Configure Java Launcher
            Provider<JavaLauncher> javaLauncherProvider = getToolchainService().launcherFor(new CurrentJvmToolchainSpec(getObjects()));
            JavaToolchainSpec toolchain = java.getToolchain();
            Provider<JavaLauncher> launcher = getToolchainService().launcherFor(toolchain).orElse(javaLauncherProvider);

            configureTaskDefaults(task, baseName, toolClasspath, launcher);
        });
    }

    protected abstract void configureTaskDefaults(T task, String baseName, FileCollection toolClasspath, Provider<JavaLauncher> toolchain);

    private void configureForSourceSets(TaskContainer tasks, SourceSetContainer sourceSets) {
        sourceSets.all(sourceSet -> {
            tasks.register(getTaskName(sourceSet), getTaskType(), task -> {
                configureForSourceSet(sourceSet, task);
            });
        });
    }

    protected abstract void configureForSourceSet(SourceSet sourceSet, T task);

    private void configureCheckTaskDependents(TaskContainer tasks, CodeQualityExtension extension) {
        // Make sure `check` depends on code quality tasks specified in extension.
        tasks.named(JavaBasePlugin.CHECK_TASK_NAME, task -> {
            task.dependsOn((Callable<Iterable<Task>>) () ->
                extension.getSourceSets().stream()
                    .map(sourceSet -> tasks.getByName(getTaskName(sourceSet)))
                    .collect(Collectors.toList())
            );
        });
    }

    public static void maybeAddOpensJvmArgs(JavaLauncher javaLauncher, ForkingWorkerSpec spec) {
        if (JavaVersion.toVersion(javaLauncher.getMetadata().getJavaRuntimeVersion()).isJava9Compatible()) {
            spec.getForkOptions().jvmArgs("--add-opens", OPEN_MODULES_ARG);
        }
    }
}
