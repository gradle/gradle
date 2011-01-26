/*
 * Copyright 2011 the original author or authors.
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


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.internal.IConventionAware
import org.gradle.api.internal.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.*

/**
 * <p>A {@link Plugin} which runs a project as a Java Application.</p>
 *
 * @author Rene Groeschke
 */
public class ApplicationPlugin implements Plugin<Project> {

    public static final String APPLICATION_PLUGIN_NAME = "application";
    public static final String APPLICATION_GROUP = APPLICATION_PLUGIN_NAME;

    public static final String TASK_RUN_NAME = "run";
    public static final String TASK_CREATESTARTSCRIPTS_NAME = "createStartScripts"
    public static final String TASK_INSTALL_NAME = "install";
    public static final String TASK_DISTZIP_NAME = "distZip";

    public void apply(final Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        ApplicationPluginConvention applicationPluginConvention = new ApplicationPluginConvention(project);
        project.getConvention().getPlugins().put("application", applicationPluginConvention);
        configureRunTask(project);
        configureCreateScriptsTask(project, applicationPluginConvention);

        def distSpec = createDistSpec(project)
        configureInstallTask(project, applicationPluginConvention, distSpec)
        configureDistZipTask(project, applicationPluginConvention, distSpec);
    }

    private def CopySpec createDistSpec(Project project) {
        Jar jar = project.getTasks().withType(Jar.class).findByName(JavaPlugin.JAR_TASK_NAME);
        CreateStartScripts startScripts = project.getTasks().withType(CreateStartScripts.class).findByName("createStartScripts");

        project.copySpec {
            into(project.name){
                into("lib") {
                    from(jar.outputs.files)
                    from(project.configurations.runtime)
                }
                into("bin") {
                    from(startScripts.outputs.files)
                    fileMode = 0755
                }
            }
        }
    }

    private void configureRunTask(Project project) {
        JavaExec run = project.getTasks().add(TASK_RUN_NAME, JavaExec.class);
        run.setDescription("Runs this project as java application");
        run.setGroup(APPLICATION_GROUP);
        run.setClasspath(project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath());
    }

    /** @Todo: refactor this task configuration to extend a copy task and use replace tokens  */
    private void configureCreateScriptsTask(final Project project, final ApplicationPluginConvention applicationPluginConvention) {
        CreateStartScripts createStartScripts = project.getTasks().add(TASK_CREATESTARTSCRIPTS_NAME, CreateStartScripts.class);
        createStartScripts.setDescription("Creates OS start scripts for the project to run as application.");
        createStartScripts.setGroup(APPLICATION_GROUP);

        Jar jar = project.getTasks().withType(Jar.class).findByName(JavaPlugin.JAR_TASK_NAME);
        createStartScripts.setClasspath(jar.getOutputs().getFiles().plus(
                project.getConfigurations().getByName("runtime")));

        createStartScripts.getConventionMapping().map("mainClassName", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return applicationPluginConvention.getMainClassName();
            }
        });
    }

    private void configureInstallTask(Project project, ApplicationPluginConvention pluginConvention, CopySpec distSpec) {
        Copy installTask = project.tasks.add(TASK_INSTALL_NAME, Copy.class)
        installTask.setDescription("Installs the project as an application with libs and OS startscripts into a specified directory.")
        installTask.setGroup(APPLICATION_GROUP)
        installTask.with(distSpec)
        installTask.conventionMapping.destinationDir = { project.file(pluginConvention.installDirPath) }
        installTask.doLast{
            project.getAnt().chmod(file: "${installTask.destinationDir.absolutePath}/${project.name}/bin/${project.name}", perm: 'ugo+x')
        }
    }

    private void configureDistZipTask(Project project, ApplicationPluginConvention applicationPluginConvention, CopySpec distSpec) {
        Zip distZipTask = project.getTasks().add(TASK_DISTZIP_NAME, Zip.class);
        distZipTask.setDescription("Bundles the project as an application with libs and OS startscripts.");
        distZipTask.setGroup(APPLICATION_GROUP);
        distZipTask.with(distSpec)
    }
}