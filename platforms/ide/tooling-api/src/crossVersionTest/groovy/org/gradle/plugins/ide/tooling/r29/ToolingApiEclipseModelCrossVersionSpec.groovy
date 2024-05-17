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

package org.gradle.plugins.ide.tooling.r29

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=2.6 <2.9")
    def "older Gradle versions throw exception when querying natures"() {
        given:
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        rootProject.projectNatures

        then:
        thrown(UnsupportedMethodException)
    }

    @TargetGradleVersion(">=2.9")
    def "applying plugins configure appropriate project natures"() {
        given:
        plugins.each { plugin -> buildFile << "apply plugin: '${plugin}'\n" }
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        def natures = rootProject.projectNatures.collect{ it.id }

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
    def "multi-module build defines different natures for each modules"() {
        given:
        buildFile << """
            project(':java-project') { apply plugin: 'java' }
            project(':groovy-project') { apply plugin: 'groovy' }
            project(':scala-project') { apply plugin: 'scala' }
        """
        createDirs("java-project", "groovy-project", "scala-project")
        settingsFile << """
            rootProject.name = 'root'
            include 'java-project', 'groovy-project', 'scala-project'
        """

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        EclipseProject javaProject = rootProject.children.find{ it.name == 'java-project' }
        EclipseProject groovyProject = rootProject.children.find{ it.name == 'groovy-project' }
        EclipseProject scalaProject = rootProject.children.find{ it.name == 'scala-project' }

        then:
        rootProject.projectNatures.collect{ it.id } == []
        javaProject.projectNatures.collect{ it.id } == ['org.eclipse.jdt.core.javanature']
        groovyProject.projectNatures.collect{ it.id } == ['org.eclipse.jdt.groovy.core.groovyNature', 'org.eclipse.jdt.core.javanature']
        scalaProject.projectNatures.collect{ it.id } == ['org.scala-ide.sdt.core.scalanature', 'org.eclipse.jdt.core.javanature']
    }

    @TargetGradleVersion(">=2.9")
    def "custom added natures are returned"() {
        given:
        buildFile << """
            apply plugin: 'eclipse'
            eclipse {
                project {
                    natures = ['sample.nature.a', 'sample.nature.b']
                }
            }
        """
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)

        then:
        rootProject.projectNatures.collect{ it.id } == ['sample.nature.a', 'sample.nature.b']
    }

    @TargetGradleVersion(">=2.9")
    def "Java project returns Java nature along with custom natures"() {
        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'
            eclipse {
                project {
                    natures << 'sample.nature.a'
                    natures << 'sample.nature.b'
                }
            }
        """
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)

        then:
        rootProject.projectNatures.collect{ it.id } == ['org.eclipse.jdt.core.javanature', 'sample.nature.a', 'sample.nature.b']
    }

    @TargetGradleVersion(">=2.6 <2.9")
    def "older Gradle versions throw exception when querying build commands"() {
        given:
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        rootProject.buildCommands

        then:
        thrown(UnsupportedMethodException)
    }

    @TargetGradleVersion(">=2.9")
    def "applying plugins configure appropriate build commands"() {
        given:
        plugins.each { plugin -> buildFile << "apply plugin: '${plugin}'\n" }
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        def buuldCommandNames = rootProject.buildCommands.collect{ it.name }

        then:
        buuldCommandNames == expectedBuildCommandNames

        where:
        plugins                     | expectedBuildCommandNames
        []                          | []
        ['java']                    | ['org.eclipse.jdt.core.javabuilder']
        ['scala']                   | ['org.scala-ide.sdt.core.scalabuilder']
        ['groovy']                  | ['org.eclipse.jdt.core.javabuilder']
        ['java', 'scala']           | ['org.scala-ide.sdt.core.scalabuilder']
        ['java', 'scala', 'groovy'] | ['org.scala-ide.sdt.core.scalabuilder']
    }

    @TargetGradleVersion(">=2.9")
    def "multi-module build defines different build commands for each modules"(){
        given:
        buildFile << """
            project(':java-project') { apply plugin: 'java' }
            project(':scala-project') { apply plugin: 'scala' }
        """
        createDirs("java-project", "scala-project")
        settingsFile << """
            rootProject.name = 'root'
            include 'java-project', 'scala-project'
        """

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        EclipseProject javaProject = rootProject.children.find{ it.name == 'java-project' }
        EclipseProject scalaProject = rootProject.children.find{ it.name == 'scala-project' }

        then:
        rootProject.buildCommands.collect{ it.name } == []
        javaProject.buildCommands.collect{ it.name } == ['org.eclipse.jdt.core.javabuilder']
        scalaProject.buildCommands.collect{ it.name } == ['org.scala-ide.sdt.core.scalabuilder']
    }

    @TargetGradleVersion(">=2.9")
    def "custom added build commands are returned"() {
        given:
        buildFile << """
            apply plugin: 'eclipse'
            eclipse {
                project {
                    buildCommand 'buildCommandWithoutArguments'
                    buildCommand 'buildCommandWithArguments', argumentOneKey: "argumentOneValue", argumentTwoKey: "argumentTwoValue"
                }
            }
        """
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        def buildCommands = rootProject.buildCommands

        then:
        buildCommands.size() == 2
        buildCommands[0].name == 'buildCommandWithoutArguments'
        buildCommands[0].arguments.isEmpty()
        buildCommands[1].name == 'buildCommandWithArguments'
        buildCommands[1].arguments.size() == 2
        buildCommands[1].arguments['argumentOneKey'] == 'argumentOneValue'
        buildCommands[1].arguments['argumentTwoKey'] == 'argumentTwoValue'
    }

    @TargetGradleVersion(">=2.9")
    def "Java project returns Java build command along with custom ones"() {
        given:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'eclipse'
            eclipse {
                project {
                    buildCommand 'customBuildCommand'
                }
            }
        """
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        def buildCommands = rootProject.buildCommands

        then:
        buildCommands.size() == 2
        buildCommands[0].name == 'org.eclipse.jdt.core.javabuilder'
        buildCommands[0].arguments.isEmpty()
        buildCommands[1].name == 'customBuildCommand'
        buildCommands[1].arguments.isEmpty()
    }

}
