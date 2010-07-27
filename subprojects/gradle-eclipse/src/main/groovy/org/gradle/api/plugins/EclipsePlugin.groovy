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




package org.gradle.api.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.api.tasks.ide.eclipse.EclipseClasspath
import org.gradle.api.tasks.ide.eclipse.EclipseProject
import org.gradle.api.tasks.ide.eclipse.EclipseWtp
import org.gradle.plugins.eclipse.model.BuildCommand

/**
 * <p>A plugin which generates Eclipse files.</p>
 *
 * @author Hans Dockter
 */
public class EclipsePlugin implements Plugin<Project> {
    public static final String ECLIPSE_TASK_NAME = "eclipse";
    public static final String CLEAN_ECLIPSE_TASK_NAME = "cleanEclipse";
    public static final String ECLIPSE_PROJECT_TASK_NAME = "eclipseProject";
    public static final String ECLIPSE_WTP_TASK_NAME = "eclipseWtp";
    public static final String ECLIPSE_CP_TASK_NAME = "eclipseClasspath";
    public static final String ECLIPSE_WTP_MODULE_TASK_NAME = "eclipseWtpModule";

    public void apply(final Project project) {
        project.apply plugin: 'base' // We apply the base plugin to have the clean<taskname> rule
        project.task('cleanEclipse', description: 'Cleans the generated eclipse files.')
        project.task('eclipse', description: 'Generates the Eclipse files.')
        configureEclipseProject(project)
        configureEclipseClasspath(project)
        project.plugins.withType(WarPlugin.class).allPlugins {
            configureEclipseWtpModuleForWarProjects(project);
        }
    }

    private void configureEclipseProject(Project project) {
        EclipseProject eclipseProject = project.tasks.add(ECLIPSE_PROJECT_TASK_NAME, EclipseProject.class);
        eclipseProject.setProjectName(project.name);
        eclipseProject.setDescription("Generates an Eclipse .project file.")
        eclipseProject.taskGroup = 'ide'
        eclipseProject.setInputFile(project.file('.project'))
        eclipseProject.setOutputFile(project.file('.project'))

        project.plugins.withType(JavaBasePlugin.class).allPlugins {
            project.configure(project.eclipseProject) {
                buildCommands = [new BuildCommand("org.eclipse.jdt.core.javabuilder")]
                natures = ["org.eclipse.jdt.core.javanature"]
            }
        }
        project.plugins.withType(GroovyBasePlugin.class).allPlugins {
            project.configure(project.eclipseProject) {
                natures.add(natures.indexOf("org.eclipse.jdt.core.javanature"), "org.eclipse.jdt.groovy.core.groovyNature")
            }
        }
        project.plugins.withType(ScalaBasePlugin.class).allPlugins {
            project.configure(project.eclipseProject) {
                buildCommands = buildCommands.collect { command ->
                    command.name == "org.eclipse.jdt.core.javabuilder" ? new BuildCommand("ch.epfl.lamp.sdt.core.scalabuilder") : command
                }
                natures.add(natures.indexOf("org.eclipse.jdt.core.javanature"), "ch.epfl.lamp.sdt.core.scalanature")
            }
        }
        project.plugins.withType(WarPlugin.class).allPlugins {
            project.configure(project.eclipseProject) {
                buildCommand 'org.eclipse.wst.common.project.facet.core.builder'
                buildCommand 'org.eclipse.wst.validation.validationbuilder'
                natures 'org.eclipse.wst.common.project.facet.core.nature', 'org.eclipse.wst.common.modulecore.ModuleCoreNature'
            }
        }

        project."$ECLIPSE_TASK_NAME".dependsOn eclipseProject
        project."$CLEAN_ECLIPSE_TASK_NAME".dependsOn 'cleanEclipseProject'
    }

    private void configureEclipseClasspath(final Project project) {
        project.plugins.withType(JavaBasePlugin.class).allPlugins {
            EclipseClasspath eclipseClasspath = project.tasks.add(ECLIPSE_CP_TASK_NAME, EclipseClasspath.class);
            project.configure(eclipseClasspath) {
                description = "Generates an Eclipse .classpath file."
                containers 'org.eclipse.jdt.launching.JRE_CONTAINER'
                sourceSets = project.sourceSets
                inputFile = project.file('.classpath')
                outputFile = project.file('.classpath')
                variables = [GRADLE_CACHE: new File(project.gradle.getGradleUserHomeDir(), 'cache').canonicalPath]
                taskGroup = 'ide'
            }
            project."$ECLIPSE_TASK_NAME".dependsOn eclipseClasspath
            project."$CLEAN_ECLIPSE_TASK_NAME".dependsOn 'cleanEclipseClasspath'
        }
        project.plugins.withType(JavaPlugin.class).allPlugins {
            project.configure(project.eclipseClasspath) {
                plusConfigurations = [project.configurations.testRuntime]
            }
        }
    }

    private void configureEclipseWtpModuleForWarProjects(final Project project) {
        final EclipseWtp eclipseWtp = project.getTasks().add(ECLIPSE_WTP_TASK_NAME, EclipseWtp.class);

        project.configure(eclipseWtp) {
            deployName = project.name
            facet name: "jst.web", version: "2.4"
            facet name: "jst.java", version: "1.4"
            sourceSets = project.sourceSets.matching { sourceSet -> sourceSet.name == 'main' }
            plusConfigurations = [project.configurations.runtime]
            minusConfigurations = [project.configurations.providedRuntime]
            variables = [GRADLE_CACHE: new File(project.gradle.getGradleUserHomeDir(), 'cache').canonicalPath]
            taskGroup = 'ide'
            orgEclipseWstCommonComponentInputFile = project.file('.settings/org.eclipse.wst.common.component.xml')
            orgEclipseWstCommonComponentOutputFile = project.file('.settings/org.eclipse.wst.common.component.xml')
            orgEclipseWstCommonProjectFacetCoreInputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
            orgEclipseWstCommonProjectFacetCoreOutputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
        }

        project."$ECLIPSE_TASK_NAME".dependsOn eclipseWtp
        project."$CLEAN_ECLIPSE_TASK_NAME".dependsOn 'cleanEclipseWtp'
        project.cleanEclipseWtp {
            delete project.file('.settings')
        }
    }
}
