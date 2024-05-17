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

package org.gradle.plugins.ide.tooling.m4

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject

class ToolingApiEclipseLinkedResourcesCrossVersionSpec extends ToolingApiSpecification {
    def "can build linked resources"() {

        file('build.gradle').text = '''
apply plugin: 'java'
apply plugin: 'eclipse'

eclipse.project {
    linkedResource name: 'foo', location: '/path/to/foo', type : '2'
    linkedResource name: 'bar', locationUri: 'file://..', type : '3'
}
'''
        when:
        HierarchicalEclipseProject minimalProject = loadToolingModel(HierarchicalEclipseProject)

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
}
