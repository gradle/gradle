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

import org.gradle.api.internal.project.DefaultProject
import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ide.eclipse.model.Facet
import org.gradle.plugins.ide.eclipse.model.Facet.FacetType
import org.gradle.plugins.ide.eclipse.model.WbResource
import org.gradle.util.HelperUtil
import spock.lang.Issue
import spock.lang.Specification

class EclipseWtpPluginTest extends Specification {

    private final DefaultProject project = HelperUtil.createRootProject()
    private final EclipseWtpPlugin wtpPlugin = new EclipseWtpPlugin(project.services.get(Instantiator))

    def "has description"() {
        when:
        wtpPlugin.apply(project)

        then:
        wtpPlugin.lifecycleTask.description
        wtpPlugin.cleanTask.description
    }

    def "does not break when eclipse and eclipseWtp applied"() {
        expect:
        project.apply plugin: 'eclipse'
        project.apply plugin: 'eclipse-wtp'
    }

    def "the eclipse plugin is applied along with eclipseWtp plugin"() {
        when:
        wtpPlugin.apply(project)

        then:
        project.tasks.eclipse.dependsOn.contains(project.eclipseWtp)
        project.tasks.cleanEclipse.dependsOn.contains(project.cleanEclipseWtp)
    }

     def applyToWarProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        project.apply(plugin: 'war')
        project.sourceCompatibility = 1.5
        wtpPlugin.apply(project)

        then:
        [project.cleanEclipseWtpComponent, project.cleanEclipseWtpFacet].each {
            assert project.tasks.cleanEclipseWtp.dependsOn.contains(it)
        }

        checkEclipseClasspath([project.configurations.testRuntime])
        checkEclipseWtpComponentForWar()
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, "jst.java", null),
                new Facet(FacetType.fixed, "jst.web", null),
                new Facet(FacetType.installed, "jst.web", "2.4"),
                new Facet(FacetType.installed, "jst.java", "5.0")])
    }

    @Issue("GRADLE-1770")
    def "wb resource honors web app dir even if configured after plugin appliance"() {
        when:
        project.apply(plugin: 'war')
        wtpPlugin.apply(project)
        project.webAppDirName = 'foo'

        then:
        project.eclipseWtpComponent.resources == [new WbResource('/', 'foo')]
    }

    def applyToEarProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        project.apply(plugin: 'java')
        project.apply(plugin: 'ear')
        wtpPlugin.apply(project)

        then:
        [project.cleanEclipseWtpComponent, project.cleanEclipseWtpFacet].each {
            assert project.cleanEclipseWtp.dependsOn.contains(it)
        }
        checkEclipseClasspath([project.configurations.testRuntime])
        checkEclipseWtpComponentForEar()
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, "jst.ear", null),
                new Facet(FacetType.installed, "jst.ear", "5.0")])
    }

    private void checkEclipseWtpComponentForEar() {
        def eclipseWtpComponent = project.eclipseWtpComponent
        assert eclipseWtpComponent instanceof GenerateEclipseWtpComponent
        assert project.tasks.eclipseWtp.taskDependencies.getDependencies(project.tasks.eclipseWtp).contains(eclipseWtpComponent)
        assert eclipseWtpComponent.sourceDirs == project.sourceSets.main.allSource.srcDirs
        assert eclipseWtpComponent.component.rootConfigurations == [project.configurations.deploy] as Set
        assert eclipseWtpComponent.component.libConfigurations == [project.configurations.earlib] as Set
        assert eclipseWtpComponent.minusConfigurations == [] as Set
        assert eclipseWtpComponent.deployName == project.name
        assert eclipseWtpComponent.contextPath == null
        assert eclipseWtpComponent.inputFile == project.file('.settings/org.eclipse.wst.common.component')
        assert eclipseWtpComponent.outputFile == project.file('.settings/org.eclipse.wst.common.component')
        assert eclipseWtpComponent.variables == [:]
        assert eclipseWtpComponent.resources == []
        assert eclipseWtpComponent.component.classesDeployPath == "/"
        assert eclipseWtpComponent.component.libDeployPath == "/lib"
    }

    private void checkEclipseWtpFacet(def facets) {
        GenerateEclipseWtpFacet eclipseWtpFacet = project.eclipseWtpFacet
        assert eclipseWtpFacet instanceof GenerateEclipseWtpFacet
        assert project.tasks.eclipseWtp.taskDependencies.getDependencies(project.tasks.eclipse).contains(eclipseWtpFacet)
        assert eclipseWtpFacet.inputFile == project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
        assert eclipseWtpFacet.outputFile == project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
    }

    private void checkEclipseWtpComponentForWar() {
        def eclipseWtpComponent = project.eclipseWtpComponent
        assert eclipseWtpComponent instanceof GenerateEclipseWtpComponent
        assert project.tasks.eclipseWtp.taskDependencies.getDependencies(project.tasks.eclipse).contains(eclipseWtpComponent)
        assert eclipseWtpComponent.sourceDirs == project.sourceSets.main.allSource.srcDirs
        assert eclipseWtpComponent.component.rootConfigurations == [] as Set
        assert eclipseWtpComponent.component.libConfigurations == [project.configurations.runtime] as Set
        assert eclipseWtpComponent.minusConfigurations == [project.configurations.providedRuntime] as Set
        assert eclipseWtpComponent.deployName == project.name
        assert eclipseWtpComponent.contextPath == project.war.baseName
        assert eclipseWtpComponent.inputFile == project.file('.settings/org.eclipse.wst.common.component')
        assert eclipseWtpComponent.outputFile == project.file('.settings/org.eclipse.wst.common.component')
        assert eclipseWtpComponent.variables == [:]
        assert eclipseWtpComponent.resources == [new WbResource('/', project.convention.plugins.war.webAppDirName)]
        assert eclipseWtpComponent.component.classesDeployPath == "/WEB-INF/classes"
        assert eclipseWtpComponent.component.libDeployPath == "/WEB-INF/lib"
    }

    private void checkEclipseClasspath(def configurations) {
        GenerateEclipseClasspath eclipseClasspath = project.tasks.eclipseClasspath
        assert eclipseClasspath.plusConfigurations == configurations
    }

    def applyToEarProjectWithoutJavaPlugin_shouldUseAppDirInWtpComponentSource() {
        when:
        project.apply(plugin: 'ear')
        wtpPlugin.apply(project)
        then:
        project.eclipseWtpComponent.sourceDirs == [project.file(project.appDirName)] as Set
    }
}
