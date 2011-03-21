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
package org.gradle.api.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.application.CreateStartScripts
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.GradleException

/**
 * <p>A {@link Plugin} which runs a project as a Java Application.</p>
 *
 * @author Rene Groeschke
 */
public class ApplicationPlugin implements Plugin<Project> {

    public static final String APPLICATION_PLUGIN_NAME = "application"
    public static final String APPLICATION_GROUP = APPLICATION_PLUGIN_NAME

    public static final String TASK_RUN_NAME = "run"
    public static final String TASK_START_SCRIPTS_NAME = "startScripts"
    public static final String TASK_INSTALL_NAME = "install"
    public static final String TASK_DIST_ZIP_NAME = "distZip"

    public void apply(final Project project) {
        project.plugins.apply(JavaPlugin.class)
        ApplicationPluginConvention applicationPluginConvention = new ApplicationPluginConvention(project)
        project.convention.plugins.put("application", applicationPluginConvention)
        applicationPluginConvention.applicationName = project.name
        configureRunTask(project)
        configureCreateScriptsTask(project, applicationPluginConvention)

        def distSpec = createDistSpec(project, applicationPluginConvention)
        configureInstallTask(project, applicationPluginConvention, distSpec)
        configureDistZipTask(project, applicationPluginConvention, distSpec)
    }

    private def CopySpec createDistSpec(Project project, ApplicationPluginConvention applicationPluginConvention) {
        Jar jar = project.tasks.withType(Jar.class).getByName(JavaPlugin.JAR_TASK_NAME)
        CreateStartScripts startScripts = project.tasks.withType(CreateStartScripts.class).getByName(TASK_START_SCRIPTS_NAME)

        project.copySpec {
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

    private void configureRunTask(Project project) {
        JavaExec run = project.tasks.add(TASK_RUN_NAME, JavaExec.class)
        run.description = "Runs this project as a JVM application"
        run.group = APPLICATION_GROUP
        run.classpath = project.convention.getPlugin(JavaPluginConvention.class).sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).runtimeClasspath
    }

    /** @Todo: refactor this task configuration to extend a copy task and use replace tokens    */
    private void configureCreateScriptsTask(Project project, ApplicationPluginConvention applicationPluginConvention) {
        CreateStartScripts startScripts = project.tasks.add(TASK_START_SCRIPTS_NAME, CreateStartScripts.class)
        startScripts.description = "Creates OS specific scripts to run the project as a JVM application."

        Jar jar = project.tasks.withType(Jar.class).getByName(JavaPlugin.JAR_TASK_NAME)
        startScripts.classpath = jar.outputs.files + project.configurations.runtime

        startScripts.conventionMapping.mainClassName = { applicationPluginConvention.mainClassName }
        startScripts.conventionMapping.applicationName = { applicationPluginConvention.applicationName }
        startScripts.conventionMapping.outputDir = {new File(project.buildDir, 'scripts')}
    }

    private void configureInstallTask(Project project, ApplicationPluginConvention pluginConvention, CopySpec distSpec) {
        Sync installTask = project.tasks.add(TASK_INSTALL_NAME, Sync.class)
        installTask.description = "Installs the project as a JVM application along with libs and OS specific scripts."
        installTask.group = APPLICATION_GROUP
        installTask.with(distSpec)
        installTask.into { project.file("${project.buildDir}/install/${pluginConvention.applicationName}") }
        installTask.doFirst {
            if (destinationDir.directory) {
                if (!new File(destinationDir, 'lib').directory || !new File(destinationDir, 'bin')) {
                    throw new GradleException("The specified installation directory '${destinationDir}' does not appear to contain an installation for '${pluginConvention.applicationName}'.\n" +
                            "Note that the install task will replace any existing contents of this directory.\n" +
                            "You should either delete this directory manually, or change the installation directory."
                    )
                }
            }
        }
        installTask.doLast {
            project.getAnt().chmod(file: "${destinationDir.absolutePath}/bin/${pluginConvention.applicationName}", perm: 'ugo+x')
        }
    }

    private void configureDistZipTask(Project project, ApplicationPluginConvention applicationPluginConvention, CopySpec distSpec) {
        Zip distZipTask = project.tasks.add(TASK_DIST_ZIP_NAME, Zip.class)
        distZipTask.description = "Bundles the project as a JVM application with libs and OS specific scripts.";
        distZipTask.group = APPLICATION_GROUP;
        distZipTask.conventionMapping.baseName = { applicationPluginConvention.applicationName }
        def prefix = { applicationPluginConvention.applicationPrefix }
        distZipTask.into(prefix) {
            with(distSpec)
        }
    }
}