/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r214

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.GradleVersion

@TargetGradleVersion(">=2.14")
class ToolingApEclipseModelNaturesAndBuildCommandsCrossVersionSpec extends ToolingApiSpecification {

    static final def JAVA_NATURES = ['org.eclipse.jdt.core.javanature']
    static final def WTP_NATURES = ['org.eclipse.wst.common.project.facet.core.nature', 'org.eclipse.wst.common.modulecore.ModuleCoreNature', 'org.eclipse.jem.workbench.JavaEMFNature']
    static final def JAVA_BUILD_COMMANDS = ['org.eclipse.jdt.core.javabuilder']
    static final def WTP_BUILD_COMMANDS = ['org.eclipse.wst.common.project.facet.core.builder', 'org.eclipse.wst.validation.validationbuilder']

    def "Eclipse wtp natures are added to web projects"() {
        given:
        plugins.each { plugin -> buildFile << "apply plugin: '${plugin}'\n" }
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        def natures = rootProject.projectNatures.collect{ it.id }

        then:
        if (plugins.contains('ear') && targetVersion < GradleVersion.version("8.0") ) {
            assert natures == WTP_NATURES
        } else {
            assert natures == JAVA_NATURES + WTP_NATURES
        }

        where:
        plugins << [
            ['java', 'eclipse-wtp'],
            ['war'],
            ['war', 'eclipse-wtp'],
            ['ear'],
            ['ear', 'eclipse-wtp']
        ]
    }

    def "Eclipse wtp build commands are added to web projects"() {
        given:
        plugins.each { plugin -> buildFile << "apply plugin: '${plugin}'\n" }
        settingsFile << "rootProject.name = 'root'"

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        def buildCommandNames = rootProject.buildCommands.collect{ it.name }

        then:
        buildCommandNames == expectedBuildCommandNames

        where:
        plugins                 | expectedBuildCommandNames
        ['java', 'eclipse-wtp'] | JAVA_BUILD_COMMANDS + WTP_BUILD_COMMANDS
        ['war']                 | JAVA_BUILD_COMMANDS + WTP_BUILD_COMMANDS
        ['war', 'eclipse-wtp']  | JAVA_BUILD_COMMANDS + WTP_BUILD_COMMANDS
        ['ear']                 | WTP_BUILD_COMMANDS
        ['ear', 'eclipse-wtp']  | WTP_BUILD_COMMANDS
    }

}
