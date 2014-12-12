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

    @Override
    protected String getLifecycleTaskName() {
        return "eclipseWtp"
    }

    private final Instantiator instantiator

    @Inject
    EclipseWtpPlugin(Instantiator instantiator) {
        this.instantiator = instantiator
    }

    @Override protected void onApply(Project project) {
        project.pluginManager.apply(EclipsePlugin)
        def model = project.extensions.getByType(EclipseModel)
        model.wtp = instantiator.newInstance(EclipseWtp, model.classpath)

        lifecycleTask.description = 'Generates Eclipse wtp configuration files.'
        cleanTask.description = 'Cleans Eclipse wtp configuration files.'

        def delegatePlugin = project.plugins.getPlugin(EclipsePlugin)
        delegatePlugin.lifecycleTask.dependsOn(lifecycleTask)
        delegatePlugin.cleanTask.dependsOn(cleanTask)

        configureEclipseProject(project)
        configureEclipseWtpComponent(project, model)
        configureEclipseWtpFacet(project, model)

        // do this after wtp is configured because wtp config is required to update classpath properly
        configureEclipseClasspath(project, model)
    }

    private void configureEclipseClasspath(Project project, EclipseModel eclipseModel) {
        project.plugins.withType(JavaPlugin) {
            def deleteWtpDependentModule = []
            eclipseModel.classpath.file.whenMerged { Classpath classpath ->
                if (hasWarOrEarPlugin(project)) {
                    return
                }

                def minusFiles = eclipseModel.wtp.component.minusConfigurations*.files?.flatten() ?: project.files()
                def libFiles = eclipseModel.wtp.component.libConfigurations*.files?.flatten() ?: project.files()
                for (entry in classpath.entries) {
                    if (!(entry instanceof AbstractLibrary)) {
                        continue;
                    }
                    if (minusFiles.contains(entry.library.file) || !libFiles.contains(entry.library.file)) {
                        // Mark this library as not required for deployment
                        entry.entryAttributes[AbstractClasspathEntry.COMPONENT_NON_DEPENDENCY_ATTRIBUTE] = ''
                    } else {
                        // '../' and '/WEB-INF/lib' both seem to be correct (and equivalent) values here
                        //this is necessary so that the depended upon projects will have their dependencies
                        // deployed to WEB-INF/lib of the main project.
                        entry.entryAttributes[AbstractClasspathEntry.COMPONENT_DEPENDENCY_ATTRIBUTE] = '../'
                        deleteWtpDependentModule << entry.path
                    }
                }
            }
            eclipseModel.wtp.component.file.whenMerged { WtpComponent wtpComponent ->
                if (hasWarOrEarPlugin(project)) {
                    return
                }
                wtpComponent.wbModuleEntries.removeAll { wbModule ->
                    wbModule instanceof WbDependentModule && deleteWtpDependentModule.any { lib -> wbModule.handle.contains(lib) }
                }
            }
        }

        project.plugins.withType(WarPlugin) {
            eclipseModel.classpath.containers WEB_LIBS_CONTAINER

            eclipseModel.classpath.file.whenMerged { Classpath classpath ->
                for (entry in classpath.entries) {
                    if (entry instanceof AbstractLibrary) {
                        //this is necessary to avoid annoying warnings upon import to Eclipse
                        //the .classpath entries can be marked all as non-deployable dependencies
                        //because the wtp component file declares the deployable dependencies
                        entry.entryAttributes[AbstractClasspathEntry.COMPONENT_NON_DEPENDENCY_ATTRIBUTE] = ''
                    }
                }
            }
        }
    }

    private void configureEclipseWtpComponent(Project project, EclipseModel model) {
        maybeAddTask(project, this, ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent) {
            //task properties:
            description = 'Generates the Eclipse WTP component settings file.'
            inputFile = project.file('.settings/org.eclipse.wst.common.component')
            outputFile = project.file('.settings/org.eclipse.wst.common.component')

            //model properties:
            model.wtp.component = component

            component.conventionMapping.deployName = { model.project.name }
            project.plugins.withType(JavaPlugin) {
                if (hasWarOrEarPlugin(project)) {
                    return
                }

                component.libConfigurations = [project.configurations.runtime]
                component.minusConfigurations = []
                component.classesDeployPath = "/"
                component.libDeployPath = "../"
                component.conventionMapping.sourceDirs = { getMainSourceDirs(project) }
            }
            project.plugins.withType(WarPlugin) {
                component.libConfigurations = [project.configurations.runtime]
                component.minusConfigurations = [project.configurations.providedRuntime]
                component.classesDeployPath = "/WEB-INF/classes"
                component.libDeployPath = "/WEB-INF/lib"
                component.conventionMapping.contextPath = { project.war.baseName }
                component.conventionMapping.resources = { [new WbResource('/', project.convention.plugins.war.webAppDirName)] }
                component.conventionMapping.sourceDirs = { getMainSourceDirs(project) }
            }
            project.plugins.withType(EarPlugin) {
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
    }

    private void configureEclipseWtpFacet(Project project, EclipseModel eclipseModel) {
        maybeAddTask(project, this, ECLIPSE_WTP_FACET_TASK_NAME, GenerateEclipseWtpFacet) {
            //task properties:
            description = 'Generates the Eclipse WTP facet settings file.'
            inputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
            outputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')

            //model properties:
            eclipseModel.wtp.facet = facet

            project.plugins.withType(JavaPlugin) {
                if (hasWarOrEarPlugin(project)) {
                    return
                }

                facet.conventionMapping.facets = {
                    [new Facet(FacetType.fixed, "jst.java", null), new Facet(FacetType.installed, "jst.utility", "1.0"),
                     new Facet(FacetType.installed, "jst.java", toJavaFacetVersion(project.sourceCompatibility))]
                }
            }
            project.plugins.withType(WarPlugin) {
                facet.conventionMapping.facets = {
                    [new Facet(FacetType.fixed, "jst.java", null), new Facet(FacetType.fixed, "jst.web", null),
                     new Facet(FacetType.installed, "jst.web", "2.4"), new Facet(FacetType.installed, "jst.java", toJavaFacetVersion(project.sourceCompatibility))]
                }
            }
            project.plugins.withType(EarPlugin) {
                facet.conventionMapping.facets = {
                    [new Facet(FacetType.fixed, "jst.ear", null), new Facet(FacetType.installed, "jst.ear", "5.0")]
                }
            }
        }
    }

    private void maybeAddTask(Project project, IdePlugin plugin, String taskName, Class taskType, Closure action) {
        if (project.tasks.findByName(taskName)) {
            return
        }
        def task = project.tasks.create(taskName, taskType)
        project.configure(task, action)
        plugin.addWorker(task)
    }

    private void configureEclipseProject(Project project) {
        def configureClosure = {
            project.tasks.withType(GenerateEclipseProject) {
                projectModel.buildCommand 'org.eclipse.wst.common.project.facet.core.builder'
                projectModel.buildCommand 'org.eclipse.wst.validation.validationbuilder'
                projectModel.natures 'org.eclipse.wst.common.project.facet.core.nature'
                projectModel.natures 'org.eclipse.wst.common.modulecore.ModuleCoreNature'
                projectModel.natures 'org.eclipse.jem.workbench.JavaEMFNature'
            }
        }
        project.plugins.withType(JavaPlugin, configureClosure)
        project.plugins.withType(EarPlugin, configureClosure)
    }

    private boolean hasWarOrEarPlugin(Project project) {
        project.plugins.hasPlugin(WarPlugin) || project.plugins.hasPlugin(EarPlugin)
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
