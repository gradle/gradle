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
import org.gradle.util.TestUtil
import spock.lang.Issue
import spock.lang.Specification

class EclipseWtpPluginTest extends Specification {

    private final DefaultProject project = TestUtil.createRootProject()
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
        project.eclipse.wtp.component.resources == [new WbResource('/', 'foo')]
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
        def wtp = project.eclipse.wtp.component
        def eclipseWtpComponent = project.eclipseWtpComponent
        assert eclipseWtpComponent instanceof GenerateEclipseWtpComponent
        assert project.tasks.eclipseWtp.taskDependencies.getDependencies(project.tasks.eclipseWtp).contains(eclipseWtpComponent)
        assert eclipseWtpComponent.component == wtp
        assert eclipseWtpComponent.inputFile == project.file('.settings/org.eclipse.wst.common.component')
        assert eclipseWtpComponent.outputFile == project.file('.settings/org.eclipse.wst.common.component')

        assert wtp.sourceDirs == project.sourceSets.main.allSource.srcDirs
        assert wtp.rootConfigurations == [project.configurations.deploy] as Set
        assert wtp.libConfigurations == [project.configurations.earlib] as Set
        assert wtp.minusConfigurations == [] as Set
        assert wtp.deployName == project.name
        assert wtp.contextPath == null
        assert wtp.resources == []
        assert wtp.classesDeployPath == "/"
        assert wtp.libDeployPath == "/lib"
    }

    private void checkEclipseWtpFacet(def facets) {
        GenerateEclipseWtpFacet eclipseWtpFacet = project.eclipseWtpFacet
        assert eclipseWtpFacet instanceof GenerateEclipseWtpFacet
        assert project.tasks.eclipseWtp.taskDependencies.getDependencies(project.tasks.eclipse).contains(eclipseWtpFacet)
        assert eclipseWtpFacet.inputFile == project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
        assert eclipseWtpFacet.outputFile == project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
    }

    private void checkEclipseWtpComponentForWar() {
        def wtp = project.eclipse.wtp.component
        def eclipseWtpComponent = project.eclipseWtpComponent
        assert eclipseWtpComponent instanceof GenerateEclipseWtpComponent
        assert project.tasks.eclipseWtp.taskDependencies.getDependencies(project.tasks.eclipse).contains(eclipseWtpComponent)
        assert eclipseWtpComponent.component == wtp
        assert eclipseWtpComponent.inputFile == project.file('.settings/org.eclipse.wst.common.component')
        assert eclipseWtpComponent.outputFile == project.file('.settings/org.eclipse.wst.common.component')

        assert wtp.sourceDirs == project.sourceSets.main.allSource.srcDirs
        assert wtp.rootConfigurations == [] as Set
        assert wtp.libConfigurations == [project.configurations.runtime] as Set
        assert wtp.minusConfigurations == [project.configurations.providedRuntime] as Set
        assert wtp.deployName == project.name
        assert wtp.contextPath == project.war.baseName
        assert wtp.resources == [new WbResource('/', project.convention.plugins.war.webAppDirName)]
        assert wtp.classesDeployPath == "/WEB-INF/classes"
        assert wtp.libDeployPath == "/WEB-INF/lib"
    }

    private void checkEclipseClasspath(def configurations) {
        assert project.eclipse.classpath.plusConfigurations == configurations
    }

    def applyToEarProjectWithoutJavaPlugin_shouldUseAppDirInWtpComponentSource() {
        when:
        project.apply(plugin: 'ear')
        wtpPlugin.apply(project)
        then:
        project.eclipse.wtp.component.sourceDirs == [project.file(project.appDirName)] as Set
    }
}
