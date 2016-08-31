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

package org.gradle.integtests.tooling.r31

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.junit.Assume

@ToolingApiVersion(">=3.1")
class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification {

    @TargetGradleVersion(">=3.1")
    def "Provides target module name for module dependencies"() {
        Assume.assumeFalse(toolingApi.embedded) //TODO remove after 3.1 release


        file('build.gradle').text = """
subprojects {
    apply plugin: 'java'
}

project(':impl') {
    apply plugin: 'idea'

    dependencies {
        compile project(':api')
    }
}
"""
        file('settings.gradle').text = "include 'api', 'impl'"

        when:
        IdeaProject project = withConnection { connection -> connection.getModel(IdeaProject.class) }
        def module = project.children.find { it.name == 'impl' }

        then:
        def libs = module.dependencies

        IdeaModuleDependency mod = libs.find {it instanceof IdeaModuleDependency}
        mod.targetModuleName == 'api'
    }
}
