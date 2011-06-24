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

package org.gradle.integtests.tooling

import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject

/**
 * @author: Szczepan Faber, created at: 6/11/11
 */
class ToolingApiEclipseLinkedResourcesIntegrationTest extends ToolingApiSpecification {

    def "can build linked resources"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
apply plugin: 'eclipse'

eclipse.project {
    linkedResource name: 'foo', location: '/path/to/foo', type : '2'
    linkedResource name: 'bar', locationUri: 'file://..', type : '3'
}
'''
        when:
        HierarchicalEclipseProject minimalProject = withConnection { it.getModel(HierarchicalEclipseProject.class) }

        then:
        minimalProject.linkedResources.size() == 2

        minimalProject.linkedResources[0].name == 'foo'
        minimalProject.linkedResources[0].type == '2'
        minimalProject.linkedResources[0].location == '/path/to/foo'
        minimalProject.linkedResources[0].locationUri == null

        minimalProject.linkedResources[1].name == 'bar'
        minimalProject.linkedResources[1].type == '3'
        minimalProject.linkedResources[1].location == null
        minimalProject.linkedResources[1].locationUri == 'file://..'
    }

    def "cannot build linked resources for previous version"() {
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = "apply plugin: 'java'"

        when:
        def minimalProject = withConnection { connection ->
            connection.modelTypeMap.put(HierarchicalEclipseProject.class, HierarchicalEclipseProjectVersion1.class)
            connection.getModel(HierarchicalEclipseProject.class)
        }

        minimalProject.linkedResources

        then:
        def e = thrown(Exception)
        e.printStackTrace()
        e.message.contains "HierarchicalEclipseProject.getLinkedResources()"
        e.message.contains "Method not found"
    }

    def "keeps backwards compatibility"() {
        //TODO SF - proudly copy pasted from ToolingApiEclipseModelIntegrationTest :-D
        //We need to figure out the strategy for testing tooling api for previous versions
        def projectDir = dist.testDir
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
description = 'this is a project'
'''
        projectDir.file('settings.gradle').text = 'rootProject.name = \"test project\"'

        when:
        HierarchicalEclipseProject minimalProject = withConnection {
            it.modelTypeMap.put(HierarchicalEclipseProject.class, HierarchicalEclipseProjectVersion1.class)
            it.getModel(HierarchicalEclipseProject.class)
        }

        then:
        minimalProject.path == ':'
        minimalProject.name == 'test project'
        minimalProject.description == 'this is a project'
        minimalProject.projectDirectory == projectDir
        minimalProject.parent == null
        minimalProject.children.empty

        when:
        EclipseProject fullProject = withConnection {
            it.modelTypeMap.put(EclipseProject.class, EclipseProjectVersion3.class)
            it.getModel(EclipseProject.class)
        }

        then:
        fullProject.path == ':'
        fullProject.name == 'test project'
        fullProject.description == 'this is a project'
        fullProject.projectDirectory == projectDir
        fullProject.parent == null
        fullProject.children.empty
    }
}