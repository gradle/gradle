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

package org.gradle.plugins.ide.eclipse

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ear.EarPlugin
import org.gradle.plugins.ide.eclipse.model.*
import org.gradle.plugins.ide.eclipse.model.Facet.FacetType
import org.gradle.plugins.ide.internal.IdePlugin

import javax.inject.Inject

class EclipseWtpPlugin extends IdePlugin {

    static final String ECLIPSE_WTP_COMPONENT_TASK_NAME = "eclipseWtpComponent"
    static final String ECLIPSE_WTP_FACET_TASK_NAME = "eclipseWtpFacet"
    static final String WEB_LIBS_CONTAINER = 'org.eclipse.jst.j2ee.internal.web.container'

    @Override protected String getLifecycleTaskName() {
        return "eclipseWtp"
    }

    private final Instantiator instantiator
    EclipseWtp eclipseWtpModel

    @Inject
    EclipseWtpPlugin(Instantiator instantiator) {
        this.instantiator = instantiator
    }

    @Override protected void onApply(Project project) {
        EclipsePlugin delegatePlugin = project.getPlugins().apply(EclipsePlugin.class);
        delegatePlugin.model.wtp = instantiator.newInstance(EclipseWtp, delegatePlugin.model.classpath)
        eclipseWtpModel = delegatePlugin.model.wtp

        lifecycleTask.description = 'Generates Eclipse wtp configuration files.'
        cleanTask.description = 'Cleans Eclipse wtp configuration files.'

        delegatePlugin.getLifecycleTask().dependsOn(getLifecycleTask())
        delegatePlugin.getCleanTask().dependsOn(getCleanTask())

        configureEclipseProjectForPlugin(project, WarPlugin)
        configureEclipseProjectForPlugin(project, EarPlugin)
        configureEclipseClasspathForWarPlugin(project)

        configureEclipseWtpComponent(project)
        configureEclipseWtpFacet(project)
    }

    private void configureEclipseClasspathForWarPlugin(Project project) {
        project.plugins.withType(WarPlugin) {
            project.eclipse.classpath.containers WEB_LIBS_CONTAINER

            project.eclipse.classpath.file.whenMerged { Classpath classpath ->
                for (entry in classpath.entries) {
                    if (entry instanceof AbstractLibrary) {
                        //this is necessary to avoid annoying warnings upon import to Eclipse
                        //the .classpath entries can be marked all as non-deployable dependencies
                        //because the wtp component file declares the deployable dependencies
                        entry.entryAttributes[AbstractClasspathEntry.COMPONENT_NON_DEPENDENCY_ATTRIBUTE] = ''
                    }
                }
            }

            doLaterWithEachDependedUponEclipseProject(project) { Project otherProject ->
                otherProject.eclipse.classpath.file.whenMerged { Classpath classpath ->
                    for (entry in classpath.entries) {
                        if (entry instanceof AbstractLibrary) {
                            // '../' and '/WEB-INF/lib' both seem to be correct (and equivalent) values here
                            //this is necessary so that the depended upon projects will have their dependencies
                            // deployed to WEB-INF/lib of the main project.
                            entry.entryAttributes[AbstractClasspathEntry.COMPONENT_DEPENDENCY_ATTRIBUTE] = '../'
                        }
                    }
                }
            }
        }
    }

    private void configureEclipseWtpComponent(Project project) {
        configureEclipseWtpComponentWithType(project, WarPlugin)
        configureEclipseWtpComponentWithType(project, EarPlugin)
    }

    private void configureEclipseWtpComponentWithType(Project project, Class<?> type) {
        project.plugins.withType(type) {
            maybeAddTask(project, this, ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent) {
                //task properties:
                description = 'Generates the Eclipse WTP component settings file.'
                inputFile = project.file('.settings/org.eclipse.wst.common.component')
                outputFile = project.file('.settings/org.eclipse.wst.common.component')

                //model properties:
                eclipseWtpModel.component = component

                component.conventionMapping.deployName = { project.eclipse.project.name }

                if (WarPlugin.class.isAssignableFrom(type)) {
                    component.libConfigurations = [project.configurations.runtime]
                    component.minusConfigurations = [project.configurations.providedRuntime]
                    component.conventionMapping.contextPath = { project.war.baseName }
                    component.conventionMapping.resources = { [new WbResource('/', project.convention.plugins.war.webAppDirName)] }
                    component.conventionMapping.sourceDirs = { getMainSourceDirs(project) }
                } else if (EarPlugin.class.isAssignableFrom(type)) {
                    component.rootConfigurations = [project.configurations.deploy]
                    component.libConfigurations = [project.configurations.earlib]
                    component.minusConfigurations = []
                    component.classesDeployPath = "/"
                    component.libDeployPath = "/lib"
                    component.conventionMapping.sourceDirs = { [project.file { project.appDirName }] as Set }
                    project.plugins.withType(JavaPlugin) {
                        component.conventionMapping.sourceDirs = { getMainSourceDirs(project) }
                    }
                }
            }

            doLaterWithEachDependedUponEclipseProject(project) { Project otherProject ->
                def eclipseWtpPlugin = otherProject.plugins.getPlugin(EclipseWtpPlugin)
                // require Java plugin because we need source set 'main'
                // (in the absence of 'main', it probably makes no sense to write the file)
                otherProject.plugins.withType(JavaPlugin) {
                    maybeAddTask(otherProject, eclipseWtpPlugin, ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent) {
                        //task properties:
                        description = 'Generates the Eclipse WTP component settings file.'
                        inputFile = otherProject.file('.settings/org.eclipse.wst.common.component')
                        outputFile = otherProject.file('.settings/org.eclipse.wst.common.component')

                        //model properties:
                        eclipseWtpPlugin.eclipseWtpModel.component = component

                        component.conventionMapping.deployName = { otherProject.eclipse.project.name }
                        component.conventionMapping.resources = {
                            getMainSourceDirs(otherProject).collect { new WbResource("/", otherProject.relativePath(it)) }
                        }
                    }
                }
            }
        }
    }

    private void configureEclipseWtpFacet(Project project) {
        configureEclipseWtpFacetWithType(project, WarPlugin)
        configureEclipseWtpFacetWithType(project, EarPlugin)
    }

    private void configureEclipseWtpFacetWithType(Project project, Class<?> type) {
        project.plugins.withType(type) {
            maybeAddTask(project, this, ECLIPSE_WTP_FACET_TASK_NAME, GenerateEclipseWtpFacet) {
                //task properties:
                description = 'Generates the Eclipse WTP facet settings file.'
                inputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                outputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')

                //model properties:
                eclipseWtpModel.facet = facet
                if (WarPlugin.isAssignableFrom(type)) {
                    facet.conventionMapping.facets = {
                        [new Facet(FacetType.fixed, "jst.java", null), new Facet(FacetType.fixed, "jst.web", null),
                                new Facet(FacetType.installed, "jst.web", "2.4"), new Facet(FacetType.installed, "jst.java", toJavaFacetVersion(project.sourceCompatibility))]
                    }
                } else if (EarPlugin.isAssignableFrom(type)) {
                    facet.conventionMapping.facets = { [new Facet(FacetType.fixed, "jst.ear", null), new Facet(FacetType.installed, "jst.ear", "5.0")] }
                }
            }

            doLaterWithEachDependedUponEclipseProject(project) { Project otherProject ->
                def eclipseWtpPlugin = otherProject.plugins.getPlugin(EclipseWtpPlugin)
                maybeAddTask(otherProject, eclipseWtpPlugin, ECLIPSE_WTP_FACET_TASK_NAME, GenerateEclipseWtpFacet) {
                    //task properties:
                    description = 'Generates the Eclipse WTP facet settings file.'
                    inputFile = otherProject.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
                    outputFile = otherProject.file('.settings/org.eclipse.wst.common.project.facet.core.xml')

                    //model properties:
                    eclipseWtpPlugin.eclipseWtpModel.facet = facet

                    facet.conventionMapping.facets = {
                        [new Facet(FacetType.fixed, "jst.java", null), new Facet(FacetType.fixed, "jst.web", null),
                                new Facet(FacetType.installed, "jst.utility", "1.0")]
                    }
                    otherProject.plugins.withType(JavaPlugin) {
                        facet.conventionMapping.facets = {
                            [new Facet(FacetType.fixed, "jst.java", null), new Facet(FacetType.fixed, "jst.web", null),
                                    new Facet(FacetType.installed, "jst.utility", "1.0"), new Facet(FacetType.installed, "jst.java", toJavaFacetVersion(otherProject.sourceCompatibility))]
                        }
                    }
                }
            }
        }
    }

    private void maybeAddTask(Project project, IdePlugin plugin, String taskName, Class taskType, Closure action) {
        if (project.tasks.findByName(taskName)) { return }
        def task = project.tasks.create(taskName, taskType)
        project.configure(task, action)
        plugin.addWorker(task)
    }

    private void doLaterWithEachDependedUponEclipseProject(Project project, Closure action) {
        project.gradle.projectsEvaluated {
            eachDependedUponEclipseProject(project, action)
        }
    }

    private void eachDependedUponEclipseProject(Project project, Closure action) {
        def runtimeConfig = project.configurations.findByName("runtime")
        if (runtimeConfig) {
            def projectDeps = runtimeConfig.allDependencies.withType(ProjectDependency)
            def dependedUponProjects = projectDeps*.dependencyProject
            for (dependedUponProject in dependedUponProjects) {
                dependedUponProject.plugins.withType(EclipseWtpPlugin) { action(dependedUponProject) }
                eachDependedUponEclipseProject(dependedUponProject, action)
            }
        }
    }

    private void configureEclipseProjectForPlugin(Project project, Class<?> type) {
        project.plugins.withType(type) {
            project.tasks.withType(GenerateEclipseProject) {
                projectModel.buildCommand 'org.eclipse.wst.common.project.facet.core.builder'
                projectModel.buildCommand 'org.eclipse.wst.validation.validationbuilder'
                projectModel.natures 'org.eclipse.wst.common.project.facet.core.nature'
                projectModel.natures 'org.eclipse.wst.common.modulecore.ModuleCoreNature'
                projectModel.natures 'org.eclipse.jem.workbench.JavaEMFNature'

                doLaterWithEachDependedUponEclipseProject(project) { Project otherProject ->
                    otherProject.tasks.withType(GenerateEclipseProject) {
                        projectModel.buildCommand 'org.eclipse.wst.common.project.facet.core.builder'
                        projectModel.buildCommand 'org.eclipse.wst.validation.validationbuilder'
                        projectModel.natures 'org.eclipse.wst.common.project.facet.core.nature'
                        projectModel.natures 'org.eclipse.wst.common.modulecore.ModuleCoreNature'
                        projectModel.natures 'org.eclipse.jem.workbench.JavaEMFNature'
                    }
                }
            }
        }
    }

    private Set<File> getMainSourceDirs(Project project) {
        project.sourceSets.main.allSource.srcDirs as LinkedHashSet
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
}
