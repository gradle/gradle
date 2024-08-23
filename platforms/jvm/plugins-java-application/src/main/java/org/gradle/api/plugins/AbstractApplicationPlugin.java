/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.DefaultApplicationPluginConvention;
import org.gradle.api.plugins.internal.DefaultJavaApplication;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.application.CreateStartScripts;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.internal.JavaExecExecutableUtils;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import static org.gradle.api.distribution.plugins.DistributionPlugin.TASK_INSTALL_NAME;

/**
 * Abstract base class for application plugins.
 *
 * This exists as part of migration from {@link org.gradle.api.plugins.ApplicationPlugin} to {@link org.gradle.api.plugins.JavaApplicationPlugin},
 * and should be merged with {@link org.gradle.api.plugins.JavaApplicationPlugin} after that class is the only remaining plugin.
 */
@SuppressWarnings("deprecation")
public abstract class AbstractApplicationPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        TaskContainer tasks = project.getTasks();

        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(DistributionPlugin.class);

        JvmFeatureInternal mainFeature = JavaPluginHelper.getJavaComponent(project).getMainFeature();

        JavaApplication extension = addExtension(project);
        addRunTask(project, mainFeature, extension);
        addCreateScriptsTask(project, mainFeature, extension);
        configureJavaCompileTask(mainFeature.getCompileJavaTask(), extension);
        configureInstallTask(project.getProviders(), tasks.named(TASK_INSTALL_NAME, Sync.class), extension);

        DistributionContainer distributions = project.getExtensions().getByType(DistributionContainer.class);
        Distribution mainDistribution = distributions.getByName(DistributionPlugin.MAIN_DISTRIBUTION_NAME);
        configureDistribution(project, mainFeature, mainDistribution, extension);
    }

    private void configureJavaCompileTask(TaskProvider<JavaCompile> javaCompile, JavaApplication pluginExtension) {
        javaCompile.configure(j -> j.getOptions().getJavaModuleMainClass().convention(pluginExtension.getMainClass()));
    }

    private void configureInstallTask(ProviderFactory providers, TaskProvider<Sync> installTask, JavaApplication pluginExtension) {
        installTask.configure(task -> task.doFirst(
            "don't overwrite existing directories",
            new PreventDestinationOverwrite(
                providers.provider(pluginExtension::getApplicationName),
                providers.provider(pluginExtension::getExecutableDir)
            )
        ));
    }

    private static class PreventDestinationOverwrite implements Action<Task> {
        private final Provider<String> applicationName;
        private final Provider<String> executableDir;

        private PreventDestinationOverwrite(Provider<String> applicationName, Provider<String> executableDir) {
            this.applicationName = applicationName;
            this.executableDir = executableDir;
        }

        @Override
        public void execute(Task task) {
            Sync sync = (Sync) task;
            File destinationDir = sync.getDestinationDir();
            if (destinationDir.isDirectory()) {
                String[] children = destinationDir.list();
                if (children == null) {
                    throw new UncheckedIOException("Could not list directory " + destinationDir);
                }
                if (children.length > 0) {
                    if (!new File(destinationDir, "lib").isDirectory() || !new File(destinationDir, executableDir.get()).isDirectory()) {
                        throw new GradleException("The specified installation directory \'"
                            + destinationDir
                            + "\' is neither empty nor does it contain an installation for \'"
                            + applicationName.get()
                            + "\'.\n"
                            + "If you really want to install to this directory, delete it and run the install task again.\n"
                            + "Alternatively, choose a different installation directory.");
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private JavaApplication addExtension(Project project) {
        @SuppressWarnings("deprecation") org.gradle.api.plugins.ApplicationPluginConvention pluginConvention = project.getObjects().newInstance(DefaultApplicationPluginConvention.class, project);
        DeprecationLogger.whileDisabled(() -> pluginConvention.setApplicationName(project.getName()));
        DeprecationLogger.whileDisabled(() -> project.getConvention().getPlugins().put("application", pluginConvention));
        return project.getExtensions().create(JavaApplication.class, "application", DefaultJavaApplication.class, pluginConvention);
    }

    private void addRunTask(Project project, JvmFeatureInternal mainFeature, JavaApplication pluginExtension) {
        project.getTasks().register(ApplicationPlugin.TASK_RUN_NAME, JavaExec.class, run -> {
            run.setDescription("Runs this project as a JVM application");
            run.setGroup(ApplicationPlugin.APPLICATION_GROUP);

            FileCollection runtimeClasspath = project.files().from((Callable<FileCollection>) () -> {
                if (run.getMainModule().isPresent()) {
                    return jarsOnlyRuntimeClasspath(mainFeature);
                } else {
                    return runtimeClasspath(mainFeature);
                }
            });
            run.setClasspath(runtimeClasspath);
            run.getMainModule().set(pluginExtension.getMainModule());
            run.getMainClass().set(pluginExtension.getMainClass());
            run.getJvmArguments().convention(project.provider(pluginExtension::getApplicationDefaultJvmArgs));

            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            run.getModularity().getInferModulePath().convention(javaPluginExtension.getModularity().getInferModulePath());
            ObjectFactory objectFactory = project.getObjects();
            Provider<JavaToolchainSpec> toolchainOverrideSpec = project.provider(() ->
                JavaExecExecutableUtils.getExecutableOverrideToolchainSpec(run, objectFactory));
            run.getJavaLauncher().convention(getToolchainTool(project, JavaToolchainService::launcherFor, toolchainOverrideSpec));
        });
    }

    private <T> Provider<T> getToolchainTool(
        Project project,
        BiFunction<JavaToolchainService, JavaToolchainSpec, Provider<T>> toolMapper,
        Provider<JavaToolchainSpec> toolchainOverride
    ) {
        JavaToolchainService service = project.getExtensions().getByType(JavaToolchainService.class);
        JavaPluginExtension extension = project.getExtensions().getByType(JavaPluginExtension.class);
        return toolchainOverride.orElse(extension.getToolchain())
            .flatMap(spec -> toolMapper.apply(service, spec));
    }

    // @Todo: refactor this task configuration to extend a copy task and use replace tokens
    @SuppressWarnings("deprecation")
    private void addCreateScriptsTask(Project project, JvmFeatureInternal mainFeature, JavaApplication pluginExtension) {
        project.getTasks().register(ApplicationPlugin.TASK_START_SCRIPTS_NAME, CreateStartScripts.class, startScripts -> {
            startScripts.setDescription("Creates OS specific scripts to run the project as a JVM application.");
            startScripts.setClasspath(jarsOnlyRuntimeClasspath(mainFeature));

            startScripts.getMainModule().set(pluginExtension.getMainModule());
            startScripts.getMainClass().set(pluginExtension.getMainClass());

            startScripts.getConventionMapping().map("applicationName", pluginExtension::getApplicationName);

            startScripts.getConventionMapping().map("outputDir", () -> new File(project.getBuildDir(), "scripts"));

            startScripts.getConventionMapping().map("executableDir", pluginExtension::getExecutableDir);

            startScripts.getConventionMapping().map("defaultJvmOpts", pluginExtension::getApplicationDefaultJvmArgs);

            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            startScripts.getModularity().getInferModulePath().convention(javaPluginExtension.getModularity().getInferModulePath());
        });
    }

    private FileCollection runtimeClasspath(JvmFeatureInternal mainFeature) {
        return mainFeature.getSourceSet().getRuntimeClasspath();
    }

    private FileCollection jarsOnlyRuntimeClasspath(JvmFeatureInternal mainFeature) {
        return mainFeature.getJarTask().get().getOutputs().getFiles().plus(mainFeature.getRuntimeClasspathConfiguration());
    }

    private CopySpec configureDistribution(Project project, JvmFeatureInternal mainFeature, Distribution mainDistribution, JavaApplication pluginExtension) {
        mainDistribution.getDistributionBaseName().convention(project.provider(pluginExtension::getApplicationName));
        CopySpec distSpec = mainDistribution.getContents();

        TaskProvider<Jar> jar = mainFeature.getJarTask();
        TaskProvider<Task> startScripts = project.getTasks().named(ApplicationPlugin.TASK_START_SCRIPTS_NAME);

        CopySpec libChildSpec = project.copySpec();
        libChildSpec.into("lib");
        libChildSpec.from(jar);
        libChildSpec.from(mainFeature.getRuntimeClasspathConfiguration());

        CopySpec binChildSpec = project.copySpec();

        binChildSpec.into((Callable<Object>) pluginExtension::getExecutableDir);
        binChildSpec.from(startScripts);
        binChildSpec.filePermissions(permissions -> permissions.unix("rwxr-xr-x"));

        CopySpec childSpec = project.copySpec();
        childSpec.from(project.file("src/dist"));
        childSpec.with(libChildSpec);
        childSpec.with(binChildSpec);

        distSpec.with(childSpec);

        distSpec.with(pluginExtension.getApplicationDistribution());
        return distSpec;
    }
}
