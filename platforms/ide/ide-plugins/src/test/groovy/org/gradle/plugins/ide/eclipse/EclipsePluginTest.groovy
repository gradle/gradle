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

import org.gradle.plugins.ide.eclipse.model.BuildCommand
import org.gradle.plugins.ide.eclipse.model.internal.EclipseJavaVersionMapper
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class EclipsePluginTest extends AbstractProjectBuilderSpec {

    private EclipsePlugin eclipsePlugin

    def setup() {
        eclipsePlugin = project.objects.newInstance(EclipsePlugin)
    }

    def "registers no tasks"() {
        when:
        eclipsePlugin.apply(project)
        project.apply(plugin: 'java')
        project.evaluate()

        then:
        ['eclipse', 'cleanEclipse', 'eclipseProject', 'cleanEclipseProject', 'eclipseClasspath', 'cleanEclipseClasspath', 'eclipseJdt', 'cleanEclipseJdt'].every {
            project.tasks.findByName(it) == null
        }
    }

    def applyToBaseProject_shouldConfigureEclipseProject() {
        when:
        eclipsePlugin.apply(project)

        then:
        checkEclipseProject([], [])
    }

    def applyToJavaProject_shouldConfigureProjectAndClasspathForJava() {
        when:
        eclipsePlugin.apply(project)
        project.apply(plugin: 'java-base')
        project.evaluate()
        then:
        checkEclipseProject([new BuildCommand('org.eclipse.jdt.core.javabuilder')], ['org.eclipse.jdt.core.javanature'])
        checkEclipseClasspath([])
        project.eclipse.jdt != null

        when:
        project.apply(plugin: 'java')

        then:
        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
    }

    def applyToScalaProject_shouldConfigureProjectAndClasspathForScala() {
        def scalaIdeContainer = ['org.scala-ide.sdt.launching.SCALA_CONTAINER']

        when:
        eclipsePlugin.apply(project)
        project.apply(plugin: 'scala-base')
        project.evaluate()

        then:
        checkEclipseProject([new BuildCommand('org.scala-ide.sdt.core.scalabuilder')],
                ['org.scala-ide.sdt.core.scalanature', 'org.eclipse.jdt.core.javanature'])
        checkEclipseClasspath([], scalaIdeContainer)

        when:
        project.apply(plugin: 'scala')

        then:
        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath], scalaIdeContainer)
    }

    def applyToGroovyProject_shouldConfigureProjectAndClasspathForGroovy() {
        when:
        eclipsePlugin.apply(project)
        project.apply(plugin: 'groovy-base')
        project.evaluate()

        then:
        checkEclipseProject([new BuildCommand('org.eclipse.jdt.core.javabuilder')], ['org.eclipse.jdt.groovy.core.groovyNature',
                'org.eclipse.jdt.core.javanature'])
        checkEclipseClasspath([])

        when:
        project.apply(plugin: 'groovy')

        then:
        checkEclipseClasspath([project.configurations.compileClasspath, project.configurations.runtimeClasspath, project.configurations.testCompileClasspath, project.configurations.testRuntimeClasspath])
    }

    def "creates empty classpath model for non java projects"() {
        when:
        eclipsePlugin.apply(project)

        then:
        project.eclipse.classpath
        project.eclipse.classpath.defaultOutputDir
    }

    def "configures internal class folders"() {
        when:
        eclipsePlugin.apply(project)
        project.apply(plugin: 'java')

        project.sourceSets.main.output.dir 'generated-folder'
        project.sourceSets.main.output.dir 'ws-generated'

        project.sourceSets.test.output.dir 'generated-test'
        project.sourceSets.test.output.dir 'test-resources'

        project.sourceSets.test.output.dir '../some/external/dir'

        then:
        def folders = project.eclipse.classpath.classFolders
        folders == [project.file('generated-folder'), project.file('ws-generated'), project.file('generated-test'), project.file('test-resources'), project.file('../some/external/dir')]
    }

    def "configures internal class folders for custom source sets"() {
        when:
        eclipsePlugin.apply(project)
        project.apply(plugin: 'java')
        project.sourceSets.create('custom')
        project.sourceSets.custom.output.dir 'custom-output'

        then:
        project.eclipse.classpath.classFolders == [project.file('custom-output')]
    }

    private void checkEclipseProject(List buildCommands, List natures) {
        assert project.eclipse.project.buildCommands == buildCommands
        assert project.eclipse.project.natures == natures
    }

    private void checkEclipseClasspath(def configurations, def additionalContainers = []) {
        def classpath = project.eclipse.classpath

        assert classpath.sourceSets == project.sourceSets
        assert classpath.plusConfigurations == configurations
        assert classpath.minusConfigurations == []

        def javaRuntimeName = "JavaSE-${EclipseJavaVersionMapper.toEclipseJavaVersion(project.java.targetCompatibility)}"
        assert classpath.containers == ["org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/${javaRuntimeName}/"] + additionalContainers as Set
        assert classpath.defaultOutputDir == new File(project.projectDir, 'bin/default')
    }
}
