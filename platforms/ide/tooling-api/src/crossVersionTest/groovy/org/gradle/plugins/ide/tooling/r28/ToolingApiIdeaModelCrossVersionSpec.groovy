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

package org.gradle.plugins.ide.tooling.r28


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject

class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    def "makes sure module names are unique in gradle"() {

        file('build.gradle').text = """
subprojects {
    apply plugin: 'java'
}

project(':impl') {
    dependencies {
        ${implementationConfiguration} project(':api')
    }
}

project(':contrib:impl') {
    dependencies {
        ${implementationConfiguration} project(':contrib:api')
    }
}
"""
        file('settings.gradle').text = """
        rootProject.name = "root"
        include 'api', 'impl', 'contrib:api', 'contrib:impl'"""

        when:

        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }

        then:
        def allNames = project.modules*.name
        allNames.unique().size() == 6

        IdeaModule impl = project.modules.find { it.name == 'root-impl' }
        IdeaModule contribImpl = project.modules.find { it.name == 'contrib-impl' }

        impl.dependencies[0].targetModuleName == 'root-api'
        contribImpl.dependencies[0].targetModuleName == 'contrib-api'
    }
}
