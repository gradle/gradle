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

package org.gradle.plugins.ide.tooling.r27

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject

import static org.gradle.integtests.tooling.fixture.TextUtil.normaliseFileSeparators

@TargetGradleVersion(">=2.7")
class ToolingApiEclipseLinkedResourcesCrossVersionSpec extends ToolingApiSpecification {

    def "can references sibling source folders"() {
        given:
        settingsFile.text = """
include ':subprojectA'
include ':subprojectB'
"""
        file('build.gradle').text = '''
subprojects {
    apply plugin: 'java'
}
'''
        def projectBsrcSiblingFolder = file('subprojectB/src-sibling')
        def projectBsrcFolder = file('subprojectB/src')
        def srcRootFolder1 = file('src-root')
        def srcRootFolder2 = file('src')
        def srcFolder = file('subprojectA/src')
        projectBsrcSiblingFolder.mkdirs()
        projectBsrcFolder.mkdirs()
        srcFolder.mkdirs()
        srcRootFolder1.mkdirs()
        srcRootFolder2.mkdirs()
        file('subprojectA/build.gradle').text = """
sourceSets {
    main {
        java {
            srcDirs = ['src', '../subprojectB/src-sibling', '../src-root', '../src', '../subprojectB/src']
        }
    }
}
"""
        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        EclipseProject subprojectA = rootProject.children.find {EclipseProject project -> project.name == "subprojectA"}
        then:
        subprojectA.linkedResources.size() == 4
        subprojectA.sourceDirectories.size() == 5

        subprojectA.linkedResources[0].name == 'src-sibling'
        subprojectA.linkedResources[0].type == '2'
        subprojectA.linkedResources[0].location == normaliseFileSeparators(projectBsrcSiblingFolder.getAbsolutePath())
        subprojectA.linkedResources[0].locationUri == null

        subprojectA.linkedResources[1].name == 'src-root'
        subprojectA.linkedResources[1].type == '2'
        subprojectA.linkedResources[1].location == normaliseFileSeparators(srcRootFolder1.getAbsolutePath())
        subprojectA.linkedResources[1].locationUri == null

        subprojectA.linkedResources[2].name == srcRootFolder1.parentFile.name + "-src"
        subprojectA.linkedResources[2].type == '2'
        subprojectA.linkedResources[2].location == normaliseFileSeparators(srcRootFolder2.getAbsolutePath())
        subprojectA.linkedResources[2].locationUri == null

        subprojectA.linkedResources[3].name == projectBsrcFolder.parentFile.name + "-src"
        subprojectA.linkedResources[3].type == '2'
        subprojectA.linkedResources[3].location == normaliseFileSeparators(projectBsrcFolder.getAbsolutePath())
        subprojectA.linkedResources[3].locationUri == null

        subprojectA.sourceDirectories[0].path == "src"
        subprojectA.sourceDirectories[0].directory == srcFolder

        subprojectA.sourceDirectories[1].path == "src-sibling"
        subprojectA.sourceDirectories[1].directory == projectBsrcSiblingFolder

        subprojectA.sourceDirectories[2].path == "src-root"
        subprojectA.sourceDirectories[2].directory == srcRootFolder1

        subprojectA.sourceDirectories[3].path == srcRootFolder1.parentFile.name + "-src"
        subprojectA.sourceDirectories[3].directory == srcRootFolder2

        subprojectA.sourceDirectories[4].path == projectBsrcFolder.parentFile.name + "-src"
        subprojectA.sourceDirectories[4].directory == projectBsrcFolder
    }
}
