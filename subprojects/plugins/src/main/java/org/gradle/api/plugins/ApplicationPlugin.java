/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.plugins.internal.JvmPluginsHelper;
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
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import static org.gradle.api.distribution.plugins.DistributionPlugin.TASK_INSTALL_NAME;

/**
 * <p>A {@link Plugin} which runs a project as a Java Application.</p>
 *
 * <p>The plugin can be configured via its companion {@link ApplicationPluginConvention} object.</p>
 *
 * @see <a href="https://docs.gradle.org/current/userguide/application_plugin.html">Application plugin reference</a>
 */
public abstract class ApplicationPlugin implements Plugin<Project> {
    public static final String APPLICATION_PLUGIN_NAME = "application";
    public static final String APPLICATION_GROUP = APPLICATION_PLUGIN_NAME;
    public static final String TASK_RUN_NAME = "run";
    public static final String TASK_START_SCRIPTS_NAME = "startScripts";
    public static final String TASK_DIST_ZIP_NAME = "distZip";
    public static final String TASK_DIST_TAR_NAME = "distTar";

    @Override
    public void apply(final Project project) {
        TaskContainer tasks = project.getTasks();

        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(DistributionPlugin.class);

        JvmSoftwareComponentInternal component = JvmPluginsHelper.getJavaComponent(project);

        ApplicationPluginConvention pluginConvention = addConvention(project);
        JavaApplication pluginExtension = addExtensions(project, pluginConvention);
        addRunTask(project, component, pluginExtension, pluginConvention);
        addCreateScriptsTask(project, component, pluginExtension, pluginConvention);
        configureJavaCompileTask(component.getMainCompileJavaTask(), pluginExtension);
        configureInstallTask(project.getProviders(), tasks.named(TASK_INSTALL_NAME, Sync.class), pluginConvention);

        DistributionContainer distributions = (DistributionContainer) project.getExtensions().getByName("distributions");
        Distribution mainDistribution = distributions.getByName(DistributionPlugin.MAIN_DISTRIBUTION_NAME);
        configureDistribution(project, component, mainDistribution, pluginConvention);
    }

    private void configureJavaCompileTask(TaskProvider<JavaCompile> javaCompile, JavaApplication pluginExtension) {
        javaCompile.configure(j -> j.getOptions().getJavaModuleMainClass().convention(pluginExtension.getMainClass()));
    }

    private void configureInstallTask(ProviderFactory providers, TaskProvider<Sync> installTask, ApplicationPluginConvention pluginConvention) {
        installTask.configure(task -> task.doFirst(
            "don't overwrite existing directories",
            new PreventDestinationOverwrite(
                providers.provider(pluginConvention::getApplicationName),
                providers.provider(pluginConvention::getExecutableDir)
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

    private ApplicationPluginConvention addConvention(Project project) {
        ApplicationPluginConvention pluginConvention = project.getObjects().newInstance(DefaultApplicationPluginConvention.class, project);
        pluginConvention.setApplicationName(project.getName());
        project.getConvention().getPlugins().put("application", pluginConvention);
        return pluginConvention;
    }

    private JavaApplication addExtensions(Project project, ApplicationPluginConvention pluginConvention) {
        return project.getExtensions().create(JavaApplication.class, "application", DefaultJavaApplication.class, pluginConvention);
    }

    private void addRunTask(Project project, JvmSoftwareComponentInternal component, JavaApplication pluginExtension, ApplicationPluginConvention pluginConvention) {
        project.getTasks().register(TASK_RUN_NAME, JavaExec.class, run -> {
            run.setDescription("Runs this project as a JVM application");
            run.setGroup(APPLICATION_GROUP);

            FileCollection runtimeClasspath = project.files().from((Callable<FileCollection>) () -> {
                if (run.getMainModule().isPresent()) {
                    return jarsOnlyRuntimeClasspath(component);
                } else {
                    return runtimeClasspath(component);
                }
            });
            run.setClasspath(runtimeClasspath);
            run.getMainModule().set(pluginExtension.getMainModule());
            run.getMainClass().set(pluginExtension.getMainClass());
            run.getConventionMapping().map("jvmArgs", pluginConvention::getApplicationDefaultJvmArgs);

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
    private void addCreateScriptsTask(Project project, JvmSoftwareComponentInternal component, JavaApplication pluginExtension, ApplicationPluginConvention pluginConvention) {
        project.getTasks().register(TASK_START_SCRIPTS_NAME, CreateStartScripts.class, startScripts -> {
            startScripts.setDescription("Creates OS specific scripts to run the project as a JVM application.");
            startScripts.setClasspath(jarsOnlyRuntimeClasspath(component));

            startScripts.getMainModule().set(pluginExtension.getMainModule());
            startScripts.getMainClass().set(pluginExtension.getMainClass());

            startScripts.getConventionMapping().map("applicationName", pluginConvention::getApplicationName);

            startScripts.getConventionMapping().map("outputDir", () -> new File(project.getBuildDir(), "scripts"));

            startScripts.getConventionMapping().map("executableDir", pluginConvention::getExecutableDir);

            startScripts.getConventionMapping().map("defaultJvmOpts", pluginConvention::getApplicationDefaultJvmArgs);

            JavaPluginExtension javaPluginExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            startScripts.getModularity().getInferModulePath().convention(javaPluginExtension.getModularity().getInferModulePath());
        });
    }

    private FileCollection runtimeClasspath(JvmSoftwareComponentInternal component) {
        return component.getSourceSet().getRuntimeClasspath();
    }

    private FileCollection jarsOnlyRuntimeClasspath(JvmSoftwareComponentInternal component) {
        return component.getMainJarTask().get().getOutputs().getFiles().plus(component.getRuntimeClasspathConfiguration());
    }

    private CopySpec configureDistribution(Project project, JvmSoftwareComponentInternal component, Distribution mainDistribution, ApplicationPluginConvention pluginConvention) {
        mainDistribution.getDistributionBaseName().convention(project.provider(pluginConvention::getApplicationName));
        CopySpec distSpec = mainDistribution.getContents();

        TaskProvider<Jar> jar = component.getMainJarTask();
        TaskProvider<Task> startScripts = project.getTasks().named(TASK_START_SCRIPTS_NAME);

        CopySpec libChildSpec = project.copySpec();
        libChildSpec.into("lib");
        libChildSpec.from(jar);
        libChildSpec.from(component.getRuntimeClasspathConfiguration());

        CopySpec binChildSpec = project.copySpec();

        binChildSpec.into((Callable<Object>) pluginConvention::getExecutableDir);
        binChildSpec.from(startScripts);
        binChildSpec.setFileMode(0755);

        CopySpec childSpec = project.copySpec();
        childSpec.from(project.file("src/dist"));
        childSpec.with(libChildSpec);
        childSpec.with(binChildSpec);

        distSpec.with(childSpec);

        distSpec.with(pluginConvention.getApplicationDistribution());
        return distSpec;
    }
}
