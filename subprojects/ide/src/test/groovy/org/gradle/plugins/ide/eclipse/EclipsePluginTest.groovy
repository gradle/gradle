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

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.tasks.Delete
import org.gradle.plugins.ide.eclipse.model.BuildCommand
import org.gradle.plugins.ide.eclipse.model.Facet
import org.gradle.plugins.ide.eclipse.model.WbResource
import org.gradle.util.HelperUtil
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class EclipsePluginTest extends Specification {
    private final DefaultProject project = HelperUtil.createRootProject()
    private final EclipsePlugin eclipsePlugin = new EclipsePlugin()

    def "adds configurer task to root project only"() {
        given:
        def child = HelperUtil.createChildProject(project, "child")

        when:
        eclipsePlugin.apply(child)
        eclipsePlugin.apply(project)

        then:
        project.eclipseConfigurer instanceof EclipseConfigurer
        child.tasks.findByName('eclipseConfigurer') == null
    }

    def "makes sure that generator tasks are configured before they act"() {
        when:
        project.apply(plugin: 'java-base')
        eclipsePlugin.apply(project)

        then:
        [project.eclipseClasspath, project.eclipseJdt, project.eclipseProject].each {
            assert it.dependsOn.contains(project.eclipseConfigurer)
        }

        project.eclipseClasspath.dependsOn.contains(project.eclipseClasspathConfigurer)
        project.eclipseJdt.dependsOn.contains(project.eclipseJdtConfigurer)
        project.eclipseProject.dependsOn.contains(project.eclipseProjectConfigurer)
    }

    def "makes sure that generator configuration tasks run after plugin configurer"() {
        when:
        project.apply(plugin: 'java-base')
        eclipsePlugin.apply(project)

        then:
        [project.eclipseClasspathConfigurer, project.eclipseJdtConfigurer, project.eclipseProjectConfigurer].each {
            assert it.dependsOn.contains(project.eclipseConfigurer)
        }
    }

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
        checkEclipseJdt()

        when:
        project.apply(plugin: 'java')

        then:
        checkEclipseClasspath([project.configurations.testRuntime] as Set)
    }

    def applyToWarProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        project.apply(plugin: 'war')
        eclipsePlugin.apply(project)

        then:
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseProject)
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseClasspath)
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseWtpComponent)
        assertThatCleanEclipseDependsOn(project, project.cleanEclipseWtpFacet)
        checkEclipseProjectTask([
                new BuildCommand('org.eclipse.jdt.core.javabuilder'),
                new BuildCommand('org.eclipse.wst.common.project.facet.core.builder'),
                new BuildCommand('org.eclipse.wst.validation.validationbuilder')],
                ['org.eclipse.jdt.core.javanature',
                        'org.eclipse.wst.common.project.facet.core.nature',
                        'org.eclipse.wst.common.modulecore.ModuleCoreNature',
                        'org.eclipse.jem.workbench.JavaEMFNature'])
        checkEclipseClasspath([project.configurations.testRuntime] as Set)
        checkEclipseWtpComponent()
        checkEclipseWtpFacet()
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

        when:
        project.apply(plugin: 'scala')

        then:
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

        when:
        project.apply(plugin: 'groovy')

        then:
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
        assert eclipseClasspath.outputFile == project.file('.classpath')
        assert eclipseClasspath.defaultOutputDir == new File(project.projectDir, 'bin')
    }

    private void checkEclipseJdt() {
        EclipseJdt eclipseJdt = project.eclipseJdt
        assert project.eclipse.taskDependencies.getDependencies(project.eclipse).contains(eclipseJdt)
        assert eclipseJdt.sourceCompatibility == project.sourceCompatibility
        assert eclipseJdt.targetCompatibility == project.targetCompatibility
        assert eclipseJdt.outputFile == project.file('.settings/org.eclipse.jdt.core.prefs')
    }

    private void checkEclipseWtpFacet() {
        EclipseWtpFacet eclipseWtpFacet = project.eclipseWtpFacet
        assert eclipseWtpFacet instanceof EclipseWtpFacet
        assert project.eclipse.taskDependencies.getDependencies(project.eclipse).contains(eclipseWtpFacet)
        assert eclipseWtpFacet.inputFile == project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
        assert eclipseWtpFacet.outputFile == project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
        assert eclipseWtpFacet.facets == [new Facet("jst.web", "2.4"), new Facet("jst.java", "5.0")]
    }

    private void checkEclipseWtpComponent() {
        def eclipseWtpComponent = project.eclipseWtpComponent
        assert eclipseWtpComponent instanceof EclipseWtpComponent
        assert project.eclipse.taskDependencies.getDependencies(project.eclipse).contains(eclipseWtpComponent)
        assert eclipseWtpComponent.sourceDirs == project.sourceSets.main.allSource.sourceTrees.srcDirs.flatten() as Set
        assert eclipseWtpComponent.plusConfigurations == [project.configurations.runtime] as Set
        assert eclipseWtpComponent.minusConfigurations == [project.configurations.providedRuntime] as Set
        assert eclipseWtpComponent.deployName == project.name
        assert eclipseWtpComponent.contextPath == project.war.baseName
        assert eclipseWtpComponent.inputFile == project.file('.settings/org.eclipse.wst.common.component')
        assert eclipseWtpComponent.outputFile == project.file('.settings/org.eclipse.wst.common.component')
        assert eclipseWtpComponent.variables == [:]
        assert eclipseWtpComponent.resources == [new WbResource('/', project.convention.plugins.war.webAppDirName)]
    }

    void assertThatCleanEclipseDependsOn(Project project, Task dependsOnTask) {
        assert dependsOnTask instanceof Delete
        assert project.cleanEclipse.taskDependencies.getDependencies(project.cleanEclipse).contains(dependsOnTask)
    }
}

