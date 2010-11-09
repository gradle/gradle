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
import org.gradle.api.Task
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.Delete
import org.gradle.plugins.eclipse.model.BuildCommand
import org.gradle.plugins.eclipse.model.Facet
import org.gradle.util.HelperUtil
import spock.lang.Specification
import org.gradle.plugins.eclipse.model.WbResource

/**
 * @author Hans Dockter
 */
class EclipsePluginTest extends Specification {
    private final DefaultProject project = HelperUtil.createRootProject()
    private final EclipsePlugin eclipsePlugin = new EclipsePlugin()

    def applyToBaseProject_shouldOnlyHaveEclipseProjectTask() {
        when:
        eclipsePlugin.apply(project)

        then:
        project.tasks.findByPath(':eclipseClasspath') == null
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseProject)
        checkEclipseProjectTask([], [])
    }

    def applyToJavaProject_shouldOnlyHaveProjectAndClasspathTaskForJava() {
        when:
        project.apply(plugin: 'java-base')
        eclipsePlugin.apply(project)

        then:
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseProject)
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseClasspath)
        checkEclipseProjectTask([new BuildCommand('org.eclipse.jdt.core.javabuilder')], ['org.eclipse.jdt.core.javanature'])
        checkEclipseClasspath([] as Set)
        project.apply(plugin: 'java')
        checkEclipseClasspath([project.configurations.testRuntime] as Set)
    }

    def applyToWarProject_shouldHaveProjectForWebAndClasspathTask() {
        when:
        project.apply(plugin: 'war')
        eclipsePlugin.apply(project)

        then:
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseProject)
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseClasspath)
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseWtp)
        project.cleanEclipseWtp.targetFiles.contains(project.file('.settings'))
        checkEclipseProjectTask([
                new BuildCommand('org.eclipse.jdt.core.javabuilder'),
                new BuildCommand('org.eclipse.wst.common.project.facet.core.builder'),
                new BuildCommand('org.eclipse.wst.validation.validationbuilder')],
                ['org.eclipse.jdt.core.javanature',
                        'org.eclipse.wst.common.project.facet.core.nature',
                        'org.eclipse.wst.common.modulecore.ModuleCoreNature'])
        checkEclipseClasspath([project.configurations.testRuntime] as Set)
        checkEclipseWtp()
    }

    def applyToScalaProject_shouldHaveProjectAndClasspathTaskForScala() {
        when:
        project.apply(plugin: 'scala-base')
        eclipsePlugin.apply(project)

        then:
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseProject)
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseClasspath)
        checkEclipseProjectTask([new BuildCommand('ch.epfl.lamp.sdt.core.scalabuilder')],
                ['ch.epfl.lamp.sdt.core.scalanature', 'org.eclipse.jdt.core.javanature'])
        checkEclipseClasspath([] as Set)
        project.apply(plugin: 'scala')
        checkEclipseClasspath([project.configurations.testRuntime] as Set)
    }

    def applyToGroovyProject_shouldHaveProjectAndClasspathTaskForGroovy() {
        when:
        project.apply(plugin: 'groovy-base')
        eclipsePlugin.apply(project)

        then:
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseProject)
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseClasspath)
        checkEclipseProjectTask([new BuildCommand('org.eclipse.jdt.core.javabuilder')], ['org.eclipse.jdt.groovy.core.groovyNature',
                'org.eclipse.jdt.core.javanature'])
        checkEclipseClasspath([] as Set)
        project.apply(plugin: 'groovy')
        checkEclipseClasspath([project.configurations.testRuntime] as Set)
    }

    private void checkEclipseProjectTask(List buildCommands, List natures) {
        EclipseProject eclipseProjectTask = project.eclipseProject
        assert eclipseProjectTask instanceof EclipseProject
        assert project.eclipse.taskDependencies.getDependencies(project.eclipse).contains(eclipseProjectTask)
        assert eclipseProjectTask.buildCommands == buildCommands
        assert eclipseProjectTask.natures == natures
        assert eclipseProjectTask.links == [] as Set
        assert eclipseProjectTask.referencedProjects == [] as Set
        assert eclipseProjectTask.comment == null
        assert eclipseProjectTask.projectName == project.name
        assert eclipseProjectTask.inputFile == project.file('.project')
        assert eclipseProjectTask.outputFile == project.file('.project')
    }

    private void checkEclipseClasspath(def configurations) {
        EclipseClasspath eclipseClasspath = project.eclipseClasspath
        assert eclipseClasspath instanceof EclipseClasspath
        assert project.eclipse.taskDependencies.getDependencies(project.eclipse).contains(eclipseClasspath)
        assert eclipseClasspath.sourceSets == project.sourceSets
        assert eclipseClasspath.plusConfigurations == configurations
        assert eclipseClasspath.minusConfigurations == [] as Set
        assert eclipseClasspath.containers == ['org.eclipse.jdt.launching.JRE_CONTAINER'] as Set
        assert eclipseClasspath.inputFile == project.file('.classpath')
        assert eclipseClasspath.outputFile == project.file('.classpath')
        def mainSourceSet = project.sourceSets.findByName('main')
        if (mainSourceSet != null) {
            assert eclipseClasspath.defaultOutputDir == mainSourceSet.classesDir
        } else {
            assert eclipseClasspath.defaultOutputDir == new File(project.buildDir, 'eclipse')
        }
        assert eclipseClasspath.variables == [GRADLE_CACHE: new File(project.gradle.gradleUserHomeDir, 'cache').canonicalPath]
    }

    private void checkEclipseWtp() {
        EclipseWtp eclipseWtp = project.eclipseWtp
        assert eclipseWtp instanceof EclipseWtp
        assert project.eclipse.taskDependencies.getDependencies(project.eclipse).contains(eclipseWtp)
        assert eclipseWtp.sourceSets.all == [project.sourceSets.main] as Set
        assert eclipseWtp.plusConfigurations == [project.configurations.runtime] as Set
        assert eclipseWtp.minusConfigurations == [project.configurations.providedRuntime] as Set
        assert eclipseWtp.deployName == project.name
        assert eclipseWtp.orgEclipseWstCommonComponentInputFile == project.file('.settings/org.eclipse.wst.common.component.xml')
        assert eclipseWtp.orgEclipseWstCommonComponentOutputFile == project.file('.settings/org.eclipse.wst.common.component.xml')
        assert eclipseWtp.orgEclipseWstCommonProjectFacetCoreInputFile == project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
        assert eclipseWtp.orgEclipseWstCommonProjectFacetCoreOutputFile == project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
        assert eclipseWtp.facets == [new Facet("jst.web", "2.4"), new Facet("jst.java", "1.4")]
        assert eclipseWtp.variables == [GRADLE_CACHE: new File(project.gradle.gradleUserHomeDir, 'cache').canonicalPath]
        assert eclipseWtp.resources == [new WbResource('/', project.convention.plugins.war.webAppDirName)]
    }

    void assertThatCleanEclipseDependsOn(Project project, Task dependsOnTask) {
        assert dependsOnTask instanceof Delete
        assert project.cleanEclipse.taskDependencies.getDependencies(project.cleanEclipse).contains(dependsOnTask)
    }
}

