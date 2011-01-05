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
package org.gradle.plugins.eclipse

import org.gradle.api.Project
import org.gradle.api.internal.plugins.IdePlugin
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.plugins.eclipse.model.BuildCommand
import org.gradle.plugins.eclipse.model.Facet
import org.gradle.api.JavaVersion

/**
 * <p>A plugin which generates Eclipse files.</p>
 *
 * @author Hans Dockter
 */
public class EclipsePlugin extends IdePlugin {
    public static final String ECLIPSE_TASK_NAME = "eclipse";
    public static final String CLEAN_ECLIPSE_TASK_NAME = "cleanEclipse";
    public static final String ECLIPSE_PROJECT_TASK_NAME = "eclipseProject";
    public static final String ECLIPSE_WTP_TASK_NAME = "eclipseWtp";
    public static final String ECLIPSE_CP_TASK_NAME = "eclipseClasspath";
    public static final String ECLIPSE_JDT_TASK_NAME = "eclipseJdt";

    @Override protected String getLifecycleTaskName() {
        return 'eclipse'
    }

    @Override protected void onApply(Project project) {
        lifecycleTask.description = 'Generates the Eclipse files.'
        cleanTask.description = 'Cleans the generated eclipse files.'
        configureEclipseProject(project)
        configureEclipseClasspath(project)
        configureEclipseJdt(project)
        configureEclipseWtpModuleForWarProjects(project);
    }

    private void configureEclipseProject(Project project) {
        EclipseProject eclipseProject = project.tasks.add(ECLIPSE_PROJECT_TASK_NAME, EclipseProject.class);
        eclipseProject.setProjectName(project.name);
        eclipseProject.description = "Generates the Eclipse .project file."
        eclipseProject.setInputFile(project.file('.project'))
        eclipseProject.setOutputFile(project.file('.project'))
        eclipseProject.conventionMapping.comment = { project.description }

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

        addWorker(eclipseProject)
    }

    private void configureEclipseClasspath(final Project project) {
        project.plugins.withType(JavaBasePlugin.class).allPlugins {
            EclipseClasspath eclipseClasspath = project.tasks.add(ECLIPSE_CP_TASK_NAME, EclipseClasspath.class);
            project.configure(eclipseClasspath) {
                description = "Generates the Eclipse .classpath file."
                containers 'org.eclipse.jdt.launching.JRE_CONTAINER'
                sourceSets = project.sourceSets
                inputFile = project.file('.classpath')
                outputFile = project.file('.classpath')
                conventionMapping.defaultOutputDir = { new File(project.buildDir, 'eclipse') }
            }
            addWorker(eclipseClasspath)
        }
        project.plugins.withType(JavaPlugin.class).allPlugins {
            project.configure(project.eclipseClasspath) {
                plusConfigurations = [project.configurations.testRuntime]
                conventionMapping.defaultOutputDir = { project.sourceSets.main.classesDir }
            }
        }
    }

    private void configureEclipseJdt(final Project project) {
        project.plugins.withType(JavaBasePlugin.class).allPlugins {
            EclipseJdt eclipseJdt = project.tasks.add(ECLIPSE_JDT_TASK_NAME, EclipseJdt.class);
            project.configure(eclipseJdt) {
                description = "Generates the Eclipse JDT settings file."
                outputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                inputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                conventionMapping.sourceCompatibility = { project.sourceCompatibility }
                conventionMapping.targetCompatibility = { project.targetCompatibility }
            }
            addWorker(eclipseJdt)
        }
    }

    private void configureEclipseWtpModuleForWarProjects(final Project project) {
        project.plugins.withType(WarPlugin.class).allPlugins {
            final EclipseWtp eclipseWtp = project.getTasks().add(ECLIPSE_WTP_TASK_NAME, EclipseWtp.class);

            project.configure(eclipseWtp) {
                description = 'Generate the Eclipse WTP settings files.'
                deployName = project.name
                conventionMapping.contextPath = { project.war.baseName }
                conventionMapping.facets = { [new Facet("jst.web", "2.4"), new Facet("jst.java", toJavaFacetVersion(project.sourceCompatibility))]}
                sourceSets = project.sourceSets.matching { sourceSet -> sourceSet.name == 'main' }
                plusConfigurations = [project.configurations.runtime]
                minusConfigurations = [project.configurations.providedRuntime]
                resource deployPath: '/', sourcePath: project.convention.plugins.war.webAppDirName
                orgEclipseWstCommonComponentInputFile = project.file('.settings/org.eclipse.wst.common.component')
                orgEclipseWstCommonComponentOutputFile = project.file('.settings/org.eclipse.wst.common.component')
                orgEclipseWstCommonProjectFacetCoreInputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                orgEclipseWstCommonProjectFacetCoreOutputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
            }

            addWorker(eclipseWtp)
        }
    }

    def toJavaFacetVersion(JavaVersion version) {
        if (version == JavaVersion.VERSION_1_5) {
            return '5.0'
        }
        if (version == JavaVersion.VERSION_1_6) {
            return '6.0'
        }
        return version.toString()
    }
}
