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


import org.gradle.internal.reflect.Instantiator
import org.gradle.plugins.ide.eclipse.model.WbProperty
import org.gradle.plugins.ide.eclipse.model.WbResource
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil
import spock.lang.Issue

class EclipseWtpPluginTest extends AbstractProjectBuilderSpec {

    private EclipseWtpPlugin wtpPlugin

    def setup() {
        wtpPlugin = TestUtil.newInstance(EclipseWtpPlugin, project.services.get(Instantiator))
    }

    def "registers no tasks"() {
        when:
        wtpPlugin.apply(project)

        then:
        ['eclipseWtp', 'cleanEclipseWtp', 'eclipseWtpComponent', 'cleanEclipseWtpComponent'].every {
            project.tasks.findByName(it) == null
        }
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
        project.plugins.hasPlugin(EclipsePlugin)
    }

    def applyToJavaProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        project.apply(plugin: 'java')
        project.java.sourceCompatibility = 1.6
        wtpPlugin.apply(project)

        then:
        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
        checkEclipseWtpComponentForJava()
    }

    def applyFirstToJavaProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        wtpPlugin.apply(project)
        project.apply(plugin: 'java')
        project.java.sourceCompatibility = 1.7

        then:
        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
        checkEclipseWtpComponentForJava()
    }

    def applyToWarProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        project.apply(plugin: 'war')
        project.java.sourceCompatibility = 1.5
        project.apply(plugin: 'eclipse-wtp')

        then:
        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
        checkEclipseWtpComponentForWar()
    }

    def applyFirstToWarProject_shouldHaveWebProjectAndClasspathTask() {
        when:
        project.apply(plugin: 'eclipse-wtp')
        project.apply(plugin: 'war')
        project.java.sourceCompatibility = 1.8

        then:
        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
        checkEclipseWtpComponentForWar()
    }

    @Issue("GRADLE-1770")
    def "wb resource honors web app dir even if configured after plugin appliance"() {
        when:
        project.apply(plugin: 'war')
        project.apply(plugin: 'eclipse-wtp')
        project.war.webAppDirectory = project.layout.projectDirectory.dir('foo')

        then:
        project.eclipse.wtp.component.resources == [new WbResource('/', 'foo')]
    }

    def "web app dir should not disappear while manually adding a wb resource"() {
        when:
        project.apply(plugin: 'war')
        project.apply(plugin: 'eclipse-wtp')
        project.war.webAppDirectory = project.layout.projectDirectory.dir('foo')

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
        if (plugs.contains('java')) {
            checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
            checkEclipseWtpComponentForEar(project.sourceSets.main.allSource.srcDirs)
        } else {
            checkEclipseClasspath([])
            checkEclipseWtpComponentForEar(project.layout.files(project.tasks['ear'].appDirectory.asFile) as Set)
        }

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

    @Issue(['GRADLE-2186', 'GRADLE-2221'])
    def "can change WTP components when java plugin is applied"() {
        when:
        project.apply(plugin: 'java')
        wtpPlugin.apply(project)
        project.java.sourceCompatibility = 1.7

        project.eclipse.wtp {
            component {
                deployName = 'ejb-jar'
                property name: 'mood', value: ':-D'
            }
        }

        then:
        project.eclipse.wtp.component.deployName == 'ejb-jar'
        project.eclipse.wtp.component.properties == [new WbProperty('mood', ':-D')]
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
        assert wtp.resources == [new WbResource('/', project.projectDir.toPath().relativize(project.war.webAppDirectory.get().asFile.toPath()).toString())]
        assert wtp.classesDeployPath == "/WEB-INF/classes"
        assert wtp.libDeployPath == "/WEB-INF/lib"
    }

    private void checkEclipseClasspath(def configurations) {
        assert project.eclipse.classpath.plusConfigurations == configurations
    }

    private def checkAndGetEclipseWtpComponent() {
        return project.eclipse.wtp.component
    }

    def applyToEarProjectWithoutJavaPlugin_shouldUseAppDirInWtpComponentSource() {
        when:
        project.apply(plugin: 'ear')
        project.apply(plugin: 'eclipse-wtp')
        then:
        project.eclipse.wtp.component.sourceDirs == [project.file(project.tasks['ear'].appDirectory.asFile)] as Set
    }
}
