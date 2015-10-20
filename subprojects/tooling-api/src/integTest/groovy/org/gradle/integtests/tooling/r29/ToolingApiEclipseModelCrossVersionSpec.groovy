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

    @TargetGradleVersion("<2.9")
    def "older Gradle versions return default natures"() {
        given:
        file('build.gradle') << ""
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
        def natures = rootProject.getProjectNatures(['default.nature'])

        then:
        natures.size() == 1
        natures[0] == 'default.nature'
    }

    @TargetGradleVersion(">=2.9")
    def "empty project returns empty nature list"() {
        given:
        file('build.gradle') << ""
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
        def natures = rootProject.getProjectNatures(['default.nature'])

        then:
        natures.isEmpty()
    }

    @TargetGradleVersion(">=2.9")
    def "Java project returns Java nature"() {
        given:
        file('build.gradle') << "apply plugin: 'java'"
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
        def natures = rootProject.getProjectNatures(['default.nature'])

        then:
        natures.size() == 1
        natures[0] == 'org.eclipse.jdt.core.javanature'
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
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
        def natures = rootProject.getProjectNatures(['default.nature'])

        then:
        natures.size() == 2
        natures[0] == 'sample.nature.a'
        natures[1] == 'sample.nature.b'
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
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
        def natures = rootProject.getProjectNatures(['default.nature'])

        then:
        natures.size() == 3
        natures[0] == 'org.eclipse.jdt.core.javanature'
        natures[1] == 'sample.nature.a'
        natures[2] == 'sample.nature.b'
    }

    @TargetGradleVersion("<2.9")
    def "older Gradle versions return default builders"() {
        given:
        file('build.gradle') << ""
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
        BuildCommand defaultBuilder = Mock()
        def builders = rootProject.getBuildCommands([defaultBuilder])

        then:
        builders.size() == 1
        builders[0] == defaultBuilder
    }

    @TargetGradleVersion(">=2.9")
    def "empty project returns empty builder list"() {
        given:
        file('build.gradle') << ""
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
        def builders = rootProject.getBuildCommands([Mock(BuildCommand.class)])

        then:
        builders.isEmpty()
    }

    @TargetGradleVersion(">=2.9")
    def "Java project returns Java builders"() {
        given:
        file('build.gradle') << "apply plugin: 'java'"
        file('settings.gradle') << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
        def builders = rootProject.getBuildCommands([])

        then:
        builders.size() == 1
        builders[0].name == 'org.eclipse.jdt.core.javabuilder'
        builders[0].arguments.isEmpty()
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
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
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
        EclipseProject rootProject = withConnection { connection -> connection.getModel(EclipseProject.class) }
        def builders = rootProject.getBuildCommands([])

        then:
        builders.size() == 2
        builders[0].name == 'org.eclipse.jdt.core.javabuilder'
        builders[0].arguments.isEmpty()
        builders[1].name == 'customBuildCommand'
        builders[1].arguments.isEmpty()
    }
}
