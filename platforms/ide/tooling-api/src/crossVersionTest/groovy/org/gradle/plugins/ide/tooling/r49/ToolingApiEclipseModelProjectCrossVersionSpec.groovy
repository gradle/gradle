/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r49

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Issue

@TargetGradleVersion('>=4.9')
class ToolingApiEclipseModelProjectCrossVersionSpec extends ToolingApiSpecification {

    def "EclipseProject with default java attributes"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.projectNatures.size() == 1
        project.projectNatures[0].id.contains('javanature')
        project.buildCommands.size() == 1
        project.buildCommands[0].name.contains('javabuilder')
    }

    def "EclipseProject attributes defined"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           import org.gradle.plugins.ide.eclipse.model.BuildCommand

           eclipse.project {
               natures += ['nature.a']
               buildCommand 'command1', argumentKey: 'arg'
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.projectNatures.size() == 2
        project.buildCommands.size() == 2
    }

    @Issue('eclipse/buildship#694')
    def "EclipseProject attributes modified via beforeMerged"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           import org.gradle.plugins.ide.eclipse.model.BuildCommand

           eclipse.project.file.beforeMerged {
               it.natures = ['nature.a']
               it.buildCommands += new BuildCommand('command1', [:])
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.projectNatures.size() == 2
        project.buildCommands.size() == 2
    }

    @Issue('eclipse/buildship#694')
    def "EclipseProject attributes modified via whenMerged"() {
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'
           import org.gradle.plugins.ide.eclipse.model.BuildCommand

           eclipse.project.file.whenMerged {
               it.natures.clear()
               it.buildCommands.clear()
           }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        project.projectNatures.isEmpty()
        project.buildCommands.isEmpty()
    }
}
