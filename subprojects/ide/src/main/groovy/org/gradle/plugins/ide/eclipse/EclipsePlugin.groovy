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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.api.tasks.GeneratorTaskConfigurer
import org.gradle.plugins.ide.eclipse.internal.EclipseDomainModelFactory
import org.gradle.plugins.ide.internal.IdePlugin
import org.gradle.plugins.ide.eclipse.model.*

/**
 * <p>A plugin which generates Eclipse files.</p>
 *
 * @author Hans Dockter
 */
class EclipsePlugin extends IdePlugin {
    static final String ECLIPSE_TASK_NAME = "eclipse"
    static final String CLEAN_ECLIPSE_TASK_NAME = "cleanEclipse"
    static final String ECLIPSE_PROJECT_TASK_NAME = "eclipseProject"
    static final String ECLIPSE_WTP_COMPONENT_TASK_NAME = "eclipseWtpComponent"
    static final String ECLIPSE_WTP_FACET_TASK_NAME = "eclipseWtpFacet"
    static final String ECLIPSE_CP_TASK_NAME = "eclipseClasspath"
    static final String ECLIPSE_JDT_TASK_NAME = "eclipseJdt"

    EclipseDomainModel getEclipseDomainModel() {
        new EclipseDomainModelFactory().create(project)
    }

    @Override protected String getLifecycleTaskName() {
        return 'eclipse'
    }

    @Override protected void onApply(Project project) {
        lifecycleTask.description = 'Generates all Eclipse files.'
        cleanTask.description = 'Cleans all Eclipse files.'
        configureEclipseConfigurer(project)
        configureEclipseProject(project)
        configureEclipseClasspath(project)
        configureEclipseJdt(project)
        configureEclipseWtpComponent(project)
        configureEclipseWtpFacet(project)
        configureDependenciesOfConfigurationTasks(project)
    }

    private def configureDependenciesOfConfigurationTasks(Project project) {
        project.tasks.withType(DependsOnConfigurer) { task ->
            //making sure eclipse plugin configurer acts before generator tasks
            task.dependsOn(project.rootProject.eclipseConfigurer)
            //adding generator task configurer and setting the dependencies
            def generatorTaskConfigurer = task.project.task(task.name + 'Configurer', description: 'Configures the domain object before generation task can act', type: GeneratorTaskConfigurer) {
                configurationTarget = task
            }
            task.dependsOn(generatorTaskConfigurer)
            generatorTaskConfigurer.dependsOn(project.rootProject.eclipseConfigurer)
        }
    }

    def configureEclipseConfigurer(Project project) {
        def root = project.rootProject
        def task = root.tasks.findByName('eclipseConfigurer')
        //making sure configurer is created once and added to the root project only
        if (!task) {
            task = root.task('eclipseConfigurer', description: 'Performs extra configuration on eclipse generator tasks', type: EclipseConfigurer)
            addWorker(task)
        }
    }

    private void configureEclipseProject(Project project) {
        addEclipsePluginTask(project, this, ECLIPSE_PROJECT_TASK_NAME, EclipseProject) {
            projectName = project.name
            description = "Generates the Eclipse project file."
            inputFile = project.file('.project')
            outputFile = project.file('.project')
            conventionMapping.comment = { project.description }

            project.plugins.withType(JavaBasePlugin) {
                buildCommand "org.eclipse.jdt.core.javabuilder"
                natures "org.eclipse.jdt.core.javanature"
            }

            project.plugins.withType(GroovyBasePlugin) {
                natures.add(natures.indexOf("org.eclipse.jdt.core.javanature"), "org.eclipse.jdt.groovy.core.groovyNature")
            }

            project.plugins.withType(ScalaBasePlugin) {
                buildCommands.set(buildCommands.findIndexOf { it.name == "org.eclipse.jdt.core.javabuilder" },
                        new BuildCommand("ch.epfl.lamp.sdt.core.scalabuilder"))
                natures.add(natures.indexOf("org.eclipse.jdt.core.javanature"), "ch.epfl.lamp.sdt.core.scalanature")
            }

            project.plugins.withType(WarPlugin) {
                buildCommand 'org.eclipse.wst.common.project.facet.core.builder'
                buildCommand 'org.eclipse.wst.validation.validationbuilder'
                natures 'org.eclipse.wst.common.project.facet.core.nature'
                natures 'org.eclipse.wst.common.modulecore.ModuleCoreNature'
                natures 'org.eclipse.jem.workbench.JavaEMFNature'

                eachDependedUponProject(project) { Project otherProject ->
                    configureTask(otherProject, ECLIPSE_PROJECT_TASK_NAME) {
                        buildCommand 'org.eclipse.wst.common.project.facet.core.builder'
                        buildCommand 'org.eclipse.wst.validation.validationbuilder'
                        natures 'org.eclipse.wst.common.project.facet.core.nature'
                        natures 'org.eclipse.wst.common.modulecore.ModuleCoreNature'
                        natures 'org.eclipse.jem.workbench.JavaEMFNature'
                    }
                }
            }
        }
    }

    private void configureEclipseClasspath(Project project) {
        project.plugins.withType(JavaBasePlugin) {
            addEclipsePluginTask(project, this, ECLIPSE_CP_TASK_NAME, EclipseClasspath) {
                description = "Generates the Eclipse classpath file."
                containers 'org.eclipse.jdt.launching.JRE_CONTAINER'
                sourceSets = project.sourceSets
                inputFile = project.file('.classpath')
                outputFile = project.file('.classpath')
                conventionMapping.defaultOutputDir = { new File(project.projectDir, 'bin') }

                project.plugins.withType(JavaPlugin) {
                    plusConfigurations = [project.configurations.testRuntime]
                }

                project.plugins.withType(WarPlugin) {
                    eachDependedUponProject(project) { Project otherProject ->
                        configureTask(otherProject, ECLIPSE_CP_TASK_NAME) {
                            whenConfigured { Classpath classpath ->
                                for (entry in classpath.entries) {
                                    if (entry instanceof Library) {
                                        // '../' and '/WEB-INF/lib' both seem to be correct (and equivalent) values here
                                        entry.entryAttributes['org.eclipse.jst.component.dependency'] = '../'
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void configureEclipseJdt(Project project) {
        project.plugins.withType(JavaBasePlugin) {
            addEclipsePluginTask(project, this, ECLIPSE_JDT_TASK_NAME, EclipseJdt) {
                description = "Generates the Eclipse JDT settings file."
                outputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                inputFile = project.file('.settings/org.eclipse.jdt.core.prefs')
                conventionMapping.sourceCompatibility = { project.sourceCompatibility }
                conventionMapping.targetCompatibility = { project.targetCompatibility }
            }
        }
    }

    private void configureEclipseWtpComponent(Project project) {
        project.plugins.withType(WarPlugin) {
            addEclipsePluginTask(project, this, ECLIPSE_WTP_COMPONENT_TASK_NAME, EclipseWtpComponent) {
                description = 'Generates the Eclipse WTP component settings file.'
                deployName = project.name
                conventionMapping.sourceDirs = { getMainSourceDirs(project) }
                plusConfigurations = [project.configurations.runtime]
                minusConfigurations = [project.configurations.providedRuntime]
                conventionMapping.contextPath = { project.war.baseName }
                resource deployPath: '/', sourcePath: project.convention.plugins.war.webAppDirName // TODO: not lazy
                inputFile = project.file('.settings/org.eclipse.wst.common.component')
                outputFile = project.file('.settings/org.eclipse.wst.common.component')
            }

            eachDependedUponProject(project) { otherProject ->
                // require Java plugin because we need source set 'main'
                // (in the absence of 'main', it probably makes no sense to write the file)
                otherProject.plugins.withType(JavaPlugin) {
                    addEclipsePluginTask(otherProject, ECLIPSE_WTP_COMPONENT_TASK_NAME, EclipseWtpComponent) {
                        description = 'Generates the Eclipse WTP component settings file.'
                        deployName = otherProject.name
                        conventionMapping.resources = {
                            getMainSourceDirs(otherProject).collect { new WbResource("/", otherProject.relativePath(it)) }
                        }
                        inputFile = otherProject.file('.settings/org.eclipse.wst.common.component')
                        outputFile = otherProject.file('.settings/org.eclipse.wst.common.component')
                    }
                }
            }
        }
    }

    private void configureEclipseWtpFacet(Project project) {
        project.plugins.withType(WarPlugin) {
            addEclipsePluginTask(project, this, ECLIPSE_WTP_FACET_TASK_NAME, EclipseWtpFacet) {
                description = 'Generates the Eclipse WTP facet settings file.'
                inputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                outputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                conventionMapping.facets = { [new Facet("jst.web", "2.4"), new Facet("jst.java", toJavaFacetVersion(project.sourceCompatibility))] }
            }

            eachDependedUponProject(project) { otherProject ->
                addEclipsePluginTask(otherProject, ECLIPSE_WTP_FACET_TASK_NAME, EclipseWtpFacet) {
                    description = 'Generates the Eclipse WTP facet settings file.'
                    inputFile = otherProject.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                    outputFile = otherProject.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                    conventionMapping.facets = { [new Facet("jst.utility", "1.0")] }
                    otherProject.plugins.withType(JavaPlugin) {
                        conventionMapping.facets = {
                            [new Facet("jst.utility", "1.0"), new Facet("jst.java",
                                    toJavaFacetVersion(otherProject.sourceCompatibility))]
                        }
                    }
                }
            }
        }
    }

    // TODO: might have to search all class paths of all source sets for project dependendencies, not just runtime configuration
    private void eachDependedUponProject(Project project, Closure action) {
        project.gradle.projectsEvaluated {
            doEachDependedUponProject(project, action)
        }
    }

    private void doEachDependedUponProject(Project project, Closure action) {
        def runtimeConfig = project.configurations.findByName("runtime")
        if (runtimeConfig) {
            def projectDeps = runtimeConfig.getAllDependencies(ProjectDependency)
            def dependedUponProjects = projectDeps*.dependencyProject
            for (dependedUponProject in dependedUponProjects) {
                action(dependedUponProject)
                doEachDependedUponProject(dependedUponProject, action)
            }
        }
    }

    private void withTask(Project project, String taskName, Closure action) {
        project.tasks.matching { it.name == taskName }.all(action)
    }

    private void configureTask(Project project, String taskName, Closure action) {
        withTask(project, taskName) { task ->
            project.configure(task, action)
        }
    }

    // note: we only add and configure the task if it doesn't exist yet
    private void addEclipsePluginTask(Project project, EclipsePlugin plugin = null, String taskName, Class taskType, Closure action) {
        if (plugin) {
            doAddEclipsePluginTask(project, plugin, taskName, taskType, action)
        } else {
            project.plugins.withType(EclipsePlugin) { EclipsePlugin otherPlugin ->
                doAddEclipsePluginTask(project, otherPlugin, taskName, taskType, action)
            }
        }
    }

    private void doAddEclipsePluginTask(Project project, EclipsePlugin plugin, String taskName, Class taskType, Closure action) {
        if (project.tasks.findByName(taskName)) { return }

        def task = project.tasks.add(taskName, taskType) // TODO: whenTaskAdded hook will fire before task has been configured
        project.configure(task, action)
        plugin.addWorker(task)
    }

    private String toJavaFacetVersion(JavaVersion version) {
        if (version == JavaVersion.VERSION_1_5) {
            return '5.0'
        }
        if (version == JavaVersion.VERSION_1_6) {
            return '6.0'
        }
        return version.toString()
    }

    private Set<File> getMainSourceDirs(Project project) {
        project.sourceSets.main.allSource.sourceTrees.srcDirs.flatten() as LinkedHashSet
    }
}
