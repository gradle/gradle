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

import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.ConventionMapping
import org.gradle.api.internal.IConventionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.WarPluginConvention
import org.gradle.api.tasks.bundling.War
import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ear.EarPlugin
import org.gradle.plugins.ear.EarPluginConvention
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.eclipse.model.EclipseWtp
import org.gradle.plugins.ide.eclipse.model.Facet
import org.gradle.plugins.ide.eclipse.model.Facet.FacetType
import org.gradle.plugins.ide.eclipse.model.WbResource
import org.gradle.plugins.ide.eclipse.model.internal.WtpClasspathAttributeSupport
import org.gradle.plugins.ide.internal.IdePlugin

import javax.inject.Inject

@CompileStatic
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

            project.tasks.getByName(EclipsePlugin.ECLIPSE_TASK_NAME).dependsOn(lifecycleTask)
            project.tasks.getByName(cleanName(EclipsePlugin.ECLIPSE_TASK_NAME)).dependsOn(cleanTask)

            configureEclipseProject(project)
            configureEclipseWtpComponent(project, model)
            configureEclipseWtpFacet(project, model)

            // do this after wtp is configured because wtp config is required to update classpath properly
            configureEclipseClasspath(project, model)
    }

    private void configureEclipseClasspath(Project project, EclipseModel model) {
        project.plugins.withType(JavaPlugin.class) {
            project.afterEvaluate {
                model.classpath.plusConfigurations += model.wtp.component.rootConfigurations
                model.classpath.plusConfigurations += model.wtp.component.libConfigurations
                model.classpath.minusConfigurations += model.wtp.component.minusConfigurations
            }

            model.classpath.file.whenMerged { Classpath classpath ->
                new WtpClasspathAttributeSupport(project, model).enhance(classpath)
            }
        }

        project.plugins.withType(WarPlugin) {
            model.classpath.containers WEB_LIBS_CONTAINER
        }
    }


    private void configureEclipseWtpComponent(Project project, EclipseModel model) {
        maybeAddTask(project, this, ECLIPSE_WTP_COMPONENT_TASK_NAME, GenerateEclipseWtpComponent) { GenerateEclipseWtpComponent task ->
            //task properties:
            task.description = 'Generates the Eclipse WTP component settings file.'
            task.inputFile = project.file('.settings/org.eclipse.wst.common.component')
            task.outputFile = project.file('.settings/org.eclipse.wst.common.component')

            //model properties:
            model.wtp.component = task.component

            ((IConventionAware)task.component).conventionMapping.map('deployName') { model.project.name }
            project.plugins.withType(JavaPlugin) {
                if (hasWarOrEarPlugin(project)) {
                    return
                }

                task.component.libConfigurations = [project.configurations.getByName('runtime')] as Set<Configuration>
                task.component.minusConfigurations = [] as Set<Configuration>
                task.component.classesDeployPath = "/"
                task.component.libDeployPath = "../"
                ((IConventionAware)task.component).conventionMapping.map('sourceDirs') { getMainSourceDirs(project) }
            }
            project.plugins.withType(WarPlugin) {
                task.component.libConfigurations = [project.configurations.getByName('runtime')] as Set<Configuration>
                task.component.minusConfigurations = [project.configurations.getByName('providedRuntime')] as Set<Configuration>
                task.component.classesDeployPath = "/WEB-INF/classes"
                task.component.libDeployPath = "/WEB-INF/lib"
                ConventionMapping convention = ((IConventionAware)task.component).conventionMapping
                convention.map('contextPath') { ((War)project.tasks.getByName('war')).baseName }
                convention.map('resources') { [new WbResource('/', project.getConvention().getPlugin(WarPluginConvention).webAppDirName)] }
                convention.map('sourceDirs') { getMainSourceDirs(project) }
            }
            project.plugins.withType(EarPlugin) {
                task.component.rootConfigurations = [project.configurations.getByName('deploy')] as Set<Configuration>
                task.component.libConfigurations = [project.configurations.getByName('earlib')] as Set<Configuration>
                task.component.minusConfigurations = [] as Set<Configuration>
                task.component.classesDeployPath = "/"
                task.component.libDeployPath = "/lib"
                ((IConventionAware)task.component).conventionMapping.map('sourceDirs') {
                    [project.file { project.getConvention().getPlugin(EarPluginConvention).appDirName }] as Set
                }
                project.plugins.withType(JavaPlugin) {
                    ((IConventionAware)task.component).conventionMapping.map('sourceDirs') { getMainSourceDirs(project) }
                }
            }
        }
    }

    private void configureEclipseWtpFacet(Project project, EclipseModel eclipseModel) {
        maybeAddTask(project, this, ECLIPSE_WTP_FACET_TASK_NAME, GenerateEclipseWtpFacet) { GenerateEclipseWtpFacet task ->
            //task properties:
            task.description = 'Generates the Eclipse WTP facet settings file.'
            task.inputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
            task.outputFile = project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')

            //model properties:
            eclipseModel.wtp.facet = task.facet

            project.plugins.withType(JavaPlugin) {
                if (hasWarOrEarPlugin(project)) {
                    return
                }

                ((IConventionAware)task.facet).conventionMapping.map('facets') {
                    [new Facet(FacetType.fixed, "jst.java", null), new Facet(FacetType.installed, "jst.utility", "1.0"),
                     new Facet(FacetType.installed, "jst.java", toJavaFacetVersion(project.convention.getPlugin(JavaPluginConvention).sourceCompatibility))]
                }
            }
            project.plugins.withType(WarPlugin) {
                ((IConventionAware)task.facet).conventionMapping.map('facets') {
                    [new Facet(FacetType.fixed, "jst.java", null), new Facet(FacetType.fixed, "jst.web", null),
                     new Facet(FacetType.installed, "jst.web", "2.4"), new Facet(FacetType.installed, "jst.java", toJavaFacetVersion(project.convention.getPlugin(JavaPluginConvention).sourceCompatibility))]
                }
            }
            project.plugins.withType(EarPlugin) {
                ((IConventionAware)task.facet).conventionMapping.map('facets') {
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
            project.tasks.withType(GenerateEclipseProject) { GenerateEclipseProject task ->
                task.projectModel.buildCommand 'org.eclipse.wst.common.project.facet.core.builder'
                task.projectModel.buildCommand 'org.eclipse.wst.validation.validationbuilder'
                task.projectModel.natures 'org.eclipse.wst.common.project.facet.core.nature'
                task.projectModel.natures 'org.eclipse.wst.common.modulecore.ModuleCoreNature'
                task.projectModel.natures 'org.eclipse.jem.workbench.JavaEMFNature'
            }
        }
        project.plugins.withType(JavaPlugin, configureClosure)
        project.plugins.withType(EarPlugin, configureClosure)
    }

    private boolean hasWarOrEarPlugin(Project project) {
        project.plugins.hasPlugin(WarPlugin) || project.plugins.hasPlugin(EarPlugin)
    }

    private Set<File> getMainSourceDirs(Project project) {
        project.getConvention().getPlugin(JavaPluginConvention).sourceSets.getByName('main').allSource.srcDirs as LinkedHashSet
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
