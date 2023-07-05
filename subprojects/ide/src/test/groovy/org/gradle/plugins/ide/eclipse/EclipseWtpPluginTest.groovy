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

import org.gradle.api.problems.Problems
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ide.eclipse.model.Facet
import org.gradle.plugins.ide.eclipse.model.Facet.FacetType
import org.gradle.plugins.ide.eclipse.model.WbProperty
import org.gradle.plugins.ide.eclipse.model.WbResource
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil
import spock.lang.Issue

class EclipseWtpPluginTest extends AbstractProjectBuilderSpec {

    private EclipseWtpPlugin wtpPlugin

    def setup() {
        wtpPlugin = TestUtil.newInstance(EclipseWtpPlugin, project.services.get(Instantiator))
        Problems.init(new DefaultProblems(Mock(BuildOperationProgressEventEmitter)))
    }

    def "has description"() {
        when:
        wtpPlugin.apply(project)

        then:
        wtpPlugin.lifecycleTask.get().description
        wtpPlugin.cleanTask.get().description
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
        project.tasks.eclipse.taskDependencies.getDependencies(null).contains(project.eclipseWtp)
        project.tasks.cleanEclipse.taskDependencies.getDependencies(null).contains(project.cleanEclipseWtp)
    }

    def applyToJavaProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        project.apply(plugin: 'java')
        project.sourceCompatibility = 1.6
        wtpPlugin.apply(project)

        then:
        [project.tasks.cleanEclipseWtpComponent, project.tasks.cleanEclipseWtpFacet].each {
            assert project.tasks.cleanEclipseWtp.dependsOn*.get().contains(it)
        }
        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
        checkEclipseWtpComponentForJava()
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, 'jst.java', null),
                new Facet(FacetType.installed, 'jst.utility', '1.0'),
                new Facet(FacetType.installed, 'jst.java', '6.0')])
    }

    def applyFirstToJavaProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        wtpPlugin.apply(project)
        project.apply(plugin: 'java')
        project.sourceCompatibility = 1.7

        then:
        [project.tasks.cleanEclipseWtpComponent, project.tasks.cleanEclipseWtpFacet].each {
            assert project.tasks.cleanEclipseWtp.dependsOn*.get().contains(it)
        }
        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
        checkEclipseWtpComponentForJava()
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, 'jst.java', null),
                new Facet(FacetType.installed, 'jst.utility', '1.0'),
                new Facet(FacetType.installed, 'jst.java', '1.7')])
    }

    def "can add custom facets to java default facets"() {
        when:
        project.apply(plugin: 'java')
        wtpPlugin.apply(project)
        project.sourceCompatibility = 1.3

        project.eclipse.wtp {
            facet {
                facet name: 'someCoolFacet', version: '1.3'
            }
        }

        then:
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, 'jst.java', null),
                new Facet(FacetType.installed, 'jst.utility', '1.0'),
                new Facet(FacetType.installed, 'jst.java', '1.3'),
                new Facet(FacetType.installed, 'someCoolFacet', '1.3')])
    }

    def applyToWarProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        project.apply(plugin: 'war')
        project.sourceCompatibility = 1.5
        project.apply(plugin: 'eclipse-wtp')

        then:
        [project.cleanEclipseWtpComponent, project.cleanEclipseWtpFacet].each {
            assert project.tasks.cleanEclipseWtp.dependsOn*.get().contains(it)
        }

        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
        checkEclipseWtpComponentForWar()
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, "jst.java", null),
                new Facet(FacetType.fixed, "jst.web", null),
                new Facet(FacetType.installed, "jst.web", "2.4"),
                new Facet(FacetType.installed, "jst.java", "5.0")])
    }

    def applyFirstToWarProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        project.apply(plugin: 'eclipse-wtp')
        project.apply(plugin: 'war')
        project.sourceCompatibility = 1.8

        then:
        [project.cleanEclipseWtpComponent, project.cleanEclipseWtpFacet].each {
            assert project.tasks.cleanEclipseWtp.dependsOn*.get().contains(it)
        }

        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
        checkEclipseWtpComponentForWar()
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, "jst.java", null),
                new Facet(FacetType.fixed, "jst.web", null),
                new Facet(FacetType.installed, "jst.web", "2.4"),
                new Facet(FacetType.installed, "jst.java", "1.8")])
    }

    def "can add custom facets to war default facets"() {
        when:
        project.apply(plugin: 'war')
        project.apply(plugin: 'eclipse-wtp')
        project.sourceCompatibility = 1.4

        project.eclipse.wtp {
            facet {
                facet name: 'someCoolFacet', version: '1.4'
            }
        }

        then:
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, "jst.java", null),
                new Facet(FacetType.fixed, "jst.web", null),
                new Facet(FacetType.installed, "jst.web", "2.4"),
                new Facet(FacetType.installed, "jst.java", "1.4"),
                new Facet(FacetType.installed, 'someCoolFacet', '1.4')])
    }

    @Issue("GRADLE-1770")
    def "wb resource honors web app dir even if configured after plugin appliance"() {
        when:
        project.apply(plugin: 'war')
        project.apply(plugin: 'eclipse-wtp')
        project.webAppDirName = 'foo'

        then:
        project.eclipse.wtp.component.resources == [new WbResource('/', 'foo')]
    }

    def "web app dir should not disappear while manually adding a wb resource"() {
        when:
        project.apply(plugin: 'war')
        project.apply(plugin: 'eclipse-wtp')
        project.webAppDirName = 'foo'

        project.eclipse.wtp {
            component {
                resource sourcePath: "common", deployPath: "/common"
            }
        }

        then:
        project.eclipse.wtp.component.resources == [new WbResource('/', 'foo'), new WbResource('/common', 'common')]
    }

    def 'applyToEarProject in order #plugs should have web project and classpath task'() {
        when:
        plugs.each { p ->
            if (p == 'eclipse-wtp') {
                project.apply(plugin: 'eclipse-wtp')
            } else {
                project.apply(plugin: p)
            }
        }

        then:
        [project.cleanEclipseWtpComponent, project.cleanEclipseWtpFacet].each {
            assert project.cleanEclipseWtp.dependsOn*.get().contains(it)
        }

        if (plugs.contains('java')) {
            checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
            checkEclipseWtpComponentForEar(project.sourceSets.main.allSource.srcDirs)
        } else {
            checkEclipseClasspath([])
            checkEclipseWtpComponentForEar(project.layout.files(project.appDirName) as Set)
        }
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, "jst.ear", null),
                new Facet(FacetType.installed, "jst.ear", "5.0")])

        where:
        plugs << [
                ['ear', 'eclipse-wtp'],
                ['eclipse-wtp', 'ear'],

                ['java', 'ear', 'eclipse-wtp'],
                ['java', 'eclipse-wtp', 'ear'],

                ['ear', 'java', 'eclipse-wtp'],
                ['ear', 'eclipse-wtp', 'java'],

                ['eclipse-wtp', 'java', 'ear'],
                ['eclipse-wtp', 'ear', 'java']]
    }

    def "can add custom facets to ear project"() {
        when:
        project.apply(plugin: 'ear')
        project.apply(plugin: 'eclipse-wtp')

        project.eclipse.wtp {
            facet {
                facet name: 'someFancyFacet', version: '2.0'
            }
        }

        then:
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, "jst.ear", null),
                new Facet(FacetType.installed, "jst.ear", "5.0"),
                new Facet(FacetType.installed, 'someFancyFacet', '2.0')])
    }

    @Issue('https://github.com/gradle/gradle/issues/945')
    def "can overwrite ear default 'jst.ear' facet"() {
        when:
        project.apply(plugin: 'ear')
        project.apply(plugin: 'eclipse-wtp')

        project.eclipse.wtp {
            facet {
                facet name: 'jst.ear', version: '8.0'
            }
        }

        then:
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, "jst.ear", null),
                new Facet(FacetType.installed, "jst.ear", "8.0")])
    }

    @Issue('https://github.com/gradle/gradle/issues/945')
    def "can overwrite war default 'jst.web' facet"() {
        when:
        project.apply(plugin: 'war')
        project.apply(plugin: 'eclipse-wtp')
        project.sourceCompatibility = 1.4

        project.eclipse.wtp {
            facet {
                facet name: 'jst.web', version: '4.0'
            }
        }

        then:
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, "jst.java", null),
                new Facet(FacetType.fixed, "jst.web", null),
                new Facet(FacetType.installed, "jst.web", "4.0"),
                new Facet(FacetType.installed, "jst.java", "1.4")])
    }

    @Issue('gradle/gradle#17681')
    def "add 'jst.ejb' facet should remove incompatible 'jst.utility' facet"() {
        when:
        project.apply(plugin: 'java')
        project.apply(plugin: 'eclipse-wtp')
        project.sourceCompatibility = 1.8

        project.eclipse.wtp {
            facet {
                facet name: 'jst.ejb', version: '3.2'
            }
        }

        then:
        checkEclipseWtpFacet([
                new Facet(FacetType.fixed, "jst.java", null),
                new Facet(FacetType.fixed, "jst.ejb", null),
                new Facet(FacetType.installed, "jst.ejb", "3.2"),
                new Facet(FacetType.installed, "jst.java", "1.8")])
    }

    @Issue(['GRADLE-2186', 'GRADLE-2221', 'gradle/gradle#17681'])
    def "can change WTP components and add facets when java plugin is applied"() {
        when:
        project.apply(plugin: 'java')
        wtpPlugin.apply(project)
        project.sourceCompatibility = 1.7

        project.eclipse.wtp {
            component {
                deployName = 'ejb-jar'
                property name: 'mood', value: ':-D'
            }
            facet {
                facet name: 'jst.ejb', version: '3.0'
            }
        }

        then:
        project.eclipse.wtp.component.deployName == 'ejb-jar'
        project.eclipse.wtp.component.properties == [new WbProperty('mood', ':-D')]
        checkEclipseWtpFacet([new Facet(FacetType.fixed, 'jst.java', null),
                              new Facet(FacetType.fixed, "jst.ejb", null),
                              new Facet(FacetType.installed, 'jst.java', '1.7'),
                              new Facet(FacetType.installed, 'jst.ejb', '3.0')])
    }


    private void checkEclipseWtpComponentForEar(def expectedSourceDirs) {
        def wtp = checkAndGetEclipseWtpComponent()
        assert wtp.sourceDirs == expectedSourceDirs
        assert wtp.rootConfigurations == [project.configurations.deploy] as Set
        assert wtp.libConfigurations == [project.configurations.earlib] as Set
        assert wtp.minusConfigurations == [] as Set
        assert wtp.deployName == project.name
        assert wtp.contextPath == null
        assert wtp.resources == []
        assert wtp.classesDeployPath == "/"
        assert wtp.libDeployPath == "/lib"
    }

    private void checkEclipseWtpFacet(def expectedFacets) {
        def wtp = project.eclipse.wtp.facet
        def eclipseWtpFacet = project.eclipseWtpFacet
        assert eclipseWtpFacet instanceof GenerateEclipseWtpFacet
        assert eclipseWtpFacet.facet == wtp
        assert project.tasks.eclipseWtp.taskDependencies.getDependencies(project.tasks.eclipseWtp).contains(eclipseWtpFacet)
        assert eclipseWtpFacet.inputFile == project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
        assert eclipseWtpFacet.outputFile == project.file('.settings/org.eclipse.wst.common.project.facet.core.xml')
        assert wtp.replaceInconsistentFacets(wtp.facets).sort() == expectedFacets.sort()
    }

    private void checkEclipseWtpComponentForJava() {
        def wtp = checkAndGetEclipseWtpComponent()
        assert wtp.sourceDirs == project.sourceSets.main.allSource.srcDirs
        assert wtp.rootConfigurations == [] as Set
        assert wtp.libConfigurations == [project.configurations.runtimeClasspath] as Set
        assert wtp.minusConfigurations == [] as Set
        assert wtp.deployName == project.name
        assert wtp.contextPath == null
        assert wtp.resources == []
        assert wtp.classesDeployPath == "/"
        assert wtp.libDeployPath == "../"
    }

    private void checkEclipseWtpComponentForWar() {
        def wtp = checkAndGetEclipseWtpComponent()
        assert wtp.sourceDirs == project.sourceSets.main.allSource.srcDirs
        assert wtp.rootConfigurations == [] as Set
        assert wtp.libConfigurations == [project.configurations.runtimeClasspath] as Set
        assert wtp.minusConfigurations == [project.configurations.providedRuntime] as Set
        assert wtp.deployName == project.name
        assert wtp.contextPath == project.war.archiveBaseName.get()
        assert wtp.resources == [new WbResource('/', project.convention.plugins.war.webAppDirName)]
        assert wtp.classesDeployPath == "/WEB-INF/classes"
        assert wtp.libDeployPath == "/WEB-INF/lib"
    }

    private void checkEclipseClasspath(def configurations) {
        assert project.eclipse.classpath.plusConfigurations == configurations
    }

    private def checkAndGetEclipseWtpComponent() {
        def wtp = project.eclipse.wtp.component
        def eclipseWtpComponent = project.eclipseWtpComponent
        assert eclipseWtpComponent instanceof GenerateEclipseWtpComponent
        assert project.tasks.eclipseWtp.taskDependencies.getDependencies(project.tasks.eclipseWtp).contains(eclipseWtpComponent)
        assert eclipseWtpComponent.component == wtp
        assert eclipseWtpComponent.inputFile == project.file('.settings/org.eclipse.wst.common.component')
        assert eclipseWtpComponent.outputFile == project.file('.settings/org.eclipse.wst.common.component')
        return wtp
    }

    def applyToEarProjectWithoutJavaPlugin_shouldUseAppDirInWtpComponentSource() {
        when:
        project.apply(plugin: 'ear')
        project.apply(plugin: 'eclipse-wtp')
        then:
        project.eclipse.wtp.component.sourceDirs == [project.file(project.appDirName)] as Set
    }
}
