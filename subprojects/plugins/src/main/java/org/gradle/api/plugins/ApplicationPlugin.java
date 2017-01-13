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
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.DistributionContainer;
import org.gradle.api.distribution.plugins.DistributionPlugin;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.application.CreateStartScripts;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.Callable;

import static org.gradle.api.distribution.plugins.DistributionPlugin.TASK_INSTALL_NAME;

/**
 * <p>A {@link Plugin} which runs a project as a Java Application.</p>
 *
 * <p>The plugin can be configured via its companion {@link ApplicationPluginConvention} object.</p>
 */
public class ApplicationPlugin implements Plugin<Project> {
    public static final String APPLICATION_PLUGIN_NAME = "application";
    public static final String APPLICATION_GROUP = APPLICATION_PLUGIN_NAME;
    public static final String TASK_RUN_NAME = "run";
    public static final String TASK_START_SCRIPTS_NAME = "startScripts";
    public static final String TASK_DIST_ZIP_NAME = "distZip";
    public static final String TASK_DIST_TAR_NAME = "distTar";

    private Project project;
    private ApplicationPluginConvention pluginConvention;

    @Override
    public void apply(final Project project) {
        this.project = project;
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(DistributionPlugin.class);

        addPluginConvention();
        addRunTask();
        addCreateScriptsTask();

        Distribution distribution = ((DistributionContainer) project.getExtensions().getByName("distributions")).getByName(DistributionPlugin.MAIN_DISTRIBUTION_NAME);

        ((IConventionAware) distribution).getConventionMapping().map("baseName", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return pluginConvention.getApplicationName();
            }
        });
        configureDistSpec(distribution.getContents());
        configureInstallTask(project.getTasks().getAt(TASK_INSTALL_NAME));
    }

    private void configureInstallTask(Task installTask) {
        installTask.doFirst(new Action<Task>() {
            @Override
            public void execute(Task task) {
                Sync sync = (Sync) task;
                if (sync.getDestinationDir().isDirectory()) {
                    if (!new File(sync.getDestinationDir(), "lib").isDirectory() || !new File(sync.getDestinationDir(), "bin").isDirectory()) {
                        throw new GradleException("The specified installation directory \'"
                            + sync.getDestinationDir()
                            + "\' is neither empty nor does it contain an installation for \'"
                            + pluginConvention.getApplicationName()
                            + "\'.\n"
                            + "If you really want to install to this directory, delete it and run the install task again.\n"
                            + "Alternatively, choose a different installation directory.");
                    }
                }
            }
        });
        installTask.doLast(new Action<Task>() {
            @Override
            public void execute(Task task) {
                Sync sync = (Sync) task;
                HashMap<String, Object> args = new HashMap<String, Object>();
                args.put("file", "" + sync.getDestinationDir().getAbsolutePath() + "/bin/" + pluginConvention.getApplicationName());
                args.put("perm", "ugo+x");
                project.getAnt().invokeMethod("chmod", args);
            }
        });
    }

    private void addPluginConvention() {
        pluginConvention = new ApplicationPluginConvention(project);
        pluginConvention.setApplicationName(project.getName());
        project.getConvention().getPlugins().put("application", pluginConvention);
    }

    private void addRunTask() {
        JavaExec run = project.getTasks().create(TASK_RUN_NAME, JavaExec.class);
        run.setDescription("Runs this project as a JVM application");
        run.setGroup(APPLICATION_GROUP);

        JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        run.setClasspath(javaPluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath());
        run.getConventionMapping().map("main", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return pluginConvention.getMainClassName();
            }
        });
        run.getConventionMapping().map("jvmArgs", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return pluginConvention.getApplicationDefaultJvmArgs();
            }
        });
    }

    // @Todo: refactor this task configuration to extend a copy task and use replace tokens
    private void addCreateScriptsTask() {
        CreateStartScripts startScripts = project.getTasks().create(TASK_START_SCRIPTS_NAME, CreateStartScripts.class);
        startScripts.setDescription("Creates OS specific scripts to run the project as a JVM application.");
        startScripts.setClasspath(project.getTasks().getAt(JavaPlugin.JAR_TASK_NAME).getOutputs().getFiles().plus(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)));

        startScripts.getConventionMapping().map("mainClassName", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return pluginConvention.getMainClassName();
            }
        });

        startScripts.getConventionMapping().map("applicationName", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return pluginConvention.getApplicationName();
            }
        });

        startScripts.getConventionMapping().map("outputDir", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return new File(project.getBuildDir(), "scripts");
            }
        });

        startScripts.getConventionMapping().map("defaultJvmOpts", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return pluginConvention.getApplicationDefaultJvmArgs();
            }
        });
    }

    private CopySpec configureDistSpec(CopySpec distSpec) {
        Task jar = project.getTasks().getAt(JavaPlugin.JAR_TASK_NAME);
        Task startScripts = project.getTasks().getAt(TASK_START_SCRIPTS_NAME);

        CopySpec libChildSpec = project.copySpec();
        libChildSpec.into("lib");
        libChildSpec.from(jar);
        libChildSpec.from(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));

        CopySpec binChildSpec = project.copySpec();
        binChildSpec.into("bin");
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
