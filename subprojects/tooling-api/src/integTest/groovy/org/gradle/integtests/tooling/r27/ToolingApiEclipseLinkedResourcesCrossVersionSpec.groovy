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

package org.gradle.integtests.tooling.r27
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
        def srcSiblingFolder = file('subprojectB/src-sibling')
        def srcRootFolder = file('src-root')
        def srcFolder = file('subprojectA/src')
        srcSiblingFolder.mkdirs()
        srcFolder.mkdirs()
        srcRootFolder.mkdirs()
        file('subprojectA/build.gradle').text = """
sourceSets {
    main {
        java {
            srcDirs = ['src', '../subprojectB/src-sibling', '../src-root']
        }
    }
}
"""


        when:
        EclipseProject rootProject = withConnection { it.getModel(EclipseProject.class) }
        EclipseProject subprojectA = rootProject.children.find {EclipseProject project -> project.name == "subprojectA"}
        then:
        subprojectA.linkedResources.size() == 2
        subprojectA.sourceDirectories.size() == 3
        subprojectA.linkedResources[0].name == 'src-sibling'
        subprojectA.linkedResources[0].type == '2'
        subprojectA.linkedResources[0].location == normaliseFileSeparators(srcSiblingFolder.getAbsolutePath())
        subprojectA.linkedResources[0].locationUri == null

        subprojectA.sourceDirectories[0].path == "src"
        subprojectA.sourceDirectories[0].directory == srcFolder
        subprojectA.sourceDirectories[1].path == "src-sibling"
        subprojectA.sourceDirectories[1].directory == srcSiblingFolder
        subprojectA.sourceDirectories[2].path == "src-root"
        subprojectA.sourceDirectories[2].directory == srcRootFolder
    }
}
