/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.r29

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.BuildCommand
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=2.9')
class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=1.0-milestone-8 <2.9")
    def "older Gradle versions return default natures"() {
        given:
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject) }

        then:
        rootProject.getProjectNatures(['default.nature']) == ['default.nature']
        rootProject.getProjectNatures([]) == []
        rootProject.getProjectNatures(null) == null
    }

    @TargetGradleVersion(">=2.9")
    def "applying plugins configure appropriate project natures"(List<String> plugins, List<String> expectedNatures) {
        given:
        plugins.each { plugin -> file('build.gradle') << "apply plugin: '${plugin}'\n" }
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject) }
        def natures = rootProject.getProjectNatures(['default.nature'])

        then:
        natures == expectedNatures
        where:
        plugins                     | expectedNatures
        []                          | []
        ['java']                    | ['org.eclipse.jdt.core.javanature']
        ['scala']                   | ['org.scala-ide.sdt.core.scalanature', 'org.eclipse.jdt.core.javanature']
        ['groovy']                  | ['org.eclipse.jdt.groovy.core.groovyNature', 'org.eclipse.jdt.core.javanature']
        ['java', 'scala']           | ['org.scala-ide.sdt.core.scalanature', 'org.eclipse.jdt.core.javanature']
        ['java', 'groovy']          | ['org.eclipse.jdt.groovy.core.groovyNature', 'org.eclipse.jdt.core.javanature']
        ['scala', 'groovy']         | ['org.eclipse.jdt.groovy.core.groovyNature', 'org.scala-ide.sdt.core.scalanature', 'org.eclipse.jdt.core.javanature']
        ['java', 'scala', 'groovy'] | ['org.eclipse.jdt.groovy.core.groovyNature', 'org.scala-ide.sdt.core.scalanature', 'org.eclipse.jdt.core.javanature']
    }

    @TargetGradleVersion(">=2.9")
    def "multi-module build defines different natures for each modules"(){
        given:
        file('build.gradle') << """
            project(':java-project') { apply plugin: 'java' }
            project(':groovy-project') { apply plugin: 'groovy' }
            project(':scala-project') { apply plugin: 'scala' }
        """
        file('settings.gradle') << """
            rootProject.name = 'root'
            include 'java-project', 'groovy-project', 'scala-project'
        """

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject) }
        EclipseProject javaProject = rootProject.children.find{ it.name == 'java-project' }
        EclipseProject groovyProject = rootProject.children.find{ it.name == 'groovy-project' }
        EclipseProject scalaProject = rootProject.children.find{ it.name == 'scala-project' }

        then:
        rootProject.getProjectNatures(['default.nature']) == []
        javaProject.getProjectNatures(['default.nature']) == ['org.eclipse.jdt.core.javanature']
        groovyProject.getProjectNatures(['default.nature']) == ['org.eclipse.jdt.groovy.core.groovyNature', 'org.eclipse.jdt.core.javanature']
        scalaProject.getProjectNatures(['default.nature']) == ['org.scala-ide.sdt.core.scalanature', 'org.eclipse.jdt.core.javanature']
    }

    @TargetGradleVersion(">=2.9")
    def "custom added natures are returned"() {
        given:
        file('build.gradle') << """
            apply plugin: 'eclipse'
            eclipse {
                project {
                    natures = ['sample.nature.a', 'sample.nature.b']
                }
            }
        """
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject) }

        then:
        rootProject.getProjectNatures(['default.nature']) == ['sample.nature.a', 'sample.nature.b']
    }

    @TargetGradleVersion(">=2.9")
    def "Java project returns Java nature along with custom natures"() {
        given:
        file('build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'eclipse'
            eclipse {
                project {
                    natures << 'sample.nature.a'
                    natures << 'sample.nature.b'
                }
            }
        """
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject) }

        then:
        rootProject.getProjectNatures(['default.nature']) == ['org.eclipse.jdt.core.javanature', 'sample.nature.a', 'sample.nature.b']
    }

    @TargetGradleVersion(">=1.0-milestone-8 <2.9")
    def "older Gradle versions return default builders"() {
        given:
        file('settings.gradle') << "rootProject.name = 'root'"
        BuildCommand defaultBuilder = Mock()

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject) }

        then:
        rootProject.getBuildCommands([defaultBuilder]) == [defaultBuilder]
        rootProject.getBuildCommands([]) == []
        rootProject.getBuildCommands(null) == null
    }

    @TargetGradleVersion(">=2.9")
    def "applying plugins configure appropriate project builders"(List<String> plugins, List<String> expectedBuilderNames) {
        given:
        plugins.each { plugin -> file('build.gradle') << "apply plugin: '${plugin}'\n" }
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject) }
        def builderNames = rootProject.getBuildCommands([]).collect{ it.name }

        then:
        builderNames == expectedBuilderNames

        where:
        plugins                     | expectedBuilderNames
        []                          | []
        ['java']                    | ['org.eclipse.jdt.core.javabuilder']
        ['scala']                   | ['org.scala-ide.sdt.core.scalabuilder']
        ['groovy']                  | ['org.eclipse.jdt.core.javabuilder']
        ['java', 'scala']           | ['org.scala-ide.sdt.core.scalabuilder']
        ['java', 'scala', 'groovy'] | ['org.scala-ide.sdt.core.scalabuilder']
        ['java','ear']              | []
    }

    @TargetGradleVersion(">=2.9")
    def "multi-module build defines different builders for each modules"(){
        given:
        file('build.gradle') << """
            project(':java-project') { apply plugin: 'java' }
            project(':scala-project') { apply plugin: 'scala' }
        """
        file('settings.gradle') << """
            rootProject.name = 'root'
            include 'java-project', 'scala-project'
        """

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject) }
        EclipseProject javaProject = rootProject.children.find{ it.name == 'java-project' }
        EclipseProject scalaProject = rootProject.children.find{ it.name == 'scala-project' }

        then:
        rootProject.getBuildCommands([Mock(BuildCommand)]).collect{ it.name } == []
        javaProject.getBuildCommands([]).collect{ it.name } == ['org.eclipse.jdt.core.javabuilder']
        scalaProject.getBuildCommands([]).collect{ it.name } == ['org.scala-ide.sdt.core.scalabuilder']
    }

    @TargetGradleVersion(">=2.9")
    def "custom added builders are returned"() {
        given:
        file('build.gradle') << """
            apply plugin: 'eclipse'
            eclipse {
                project {
                    buildCommand 'buildCommandWithoutArguments'
                    buildCommand 'buildCommandWithArguments', argumentOneKey: "argumentOneValue", argumentTwoKey: "argumentTwoValue"
                }
            }
        """
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject) }
        def builders = rootProject.getBuildCommands([])

        then:
        builders.size() == 2
        builders[0].name == 'buildCommandWithoutArguments'
        builders[0].arguments.isEmpty()
        builders[1].name == 'buildCommandWithArguments'
        builders[1].arguments.size() == 2
        builders[1].arguments['argumentOneKey'] == 'argumentOneValue'
        builders[1].arguments['argumentTwoKey'] == 'argumentTwoValue'
    }

    @TargetGradleVersion(">=2.9")
    def "Java project returns Java builder along with custom builders"() {
        given:
        file('build.gradle') << """
            apply plugin: 'java'
            apply plugin: 'eclipse'
            eclipse {
                project {
                    buildCommand 'customBuildCommand'
                }
            }
        """
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject) }
        def builders = rootProject.getBuildCommands([])

        then:
        builders.size() == 2
        builders[0].name == 'org.eclipse.jdt.core.javabuilder'
        builders[0].arguments.isEmpty()
        builders[1].name == 'customBuildCommand'
        builders[1].arguments.isEmpty()
    }
}
