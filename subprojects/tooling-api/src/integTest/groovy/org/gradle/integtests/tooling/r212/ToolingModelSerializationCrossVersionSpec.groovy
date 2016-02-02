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

package org.gradle.integtests.tooling.r212

import org.gradle.integtests.tooling.fixture.PayloadSerializerFixture
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.util.UsesNativeServices

@ToolingApiVersion('>=2.12')
@TargetGradleVersion(">=1.3")
@UsesNativeServices
class ToolingModelSerializationCrossVersionSpec extends ToolingApiSpecification {
    PayloadSerializerFixture payloadSerializerFixture = new PayloadSerializerFixture(temporaryFolder.getTestDirectory())

    def "can re-serialize the eclipse model"() {
        projectDir.file('build.gradle').text = '''
apply plugin: 'java'
description = 'this is a project'
'''
        projectDir.file('settings.gradle').text = 'rootProject.name = \"test project\"'

        when:
        def minimalProject = withConnection { connection -> connection.getModel(HierarchicalEclipseProject) }
        minimalProject = payloadSerializerFixture.makeSerializationRoundtrip(minimalProject)

        then:
        minimalProject.name == 'test project'
        minimalProject.description == 'this is a project'
        minimalProject.projectDirectory == projectDir
        minimalProject.parent == null
        minimalProject.children.empty

        when:
        def fullProject = withConnection { connection -> connection.getModel(EclipseProject) }
        fullProject = payloadSerializerFixture.makeSerializationRoundtrip(fullProject)

        then:
        fullProject.name == 'test project'
        fullProject.description == 'this is a project'
        fullProject.projectDirectory == projectDir
        fullProject.parent == null
        fullProject.children.empty
    }
}
