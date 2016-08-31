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


package org.gradle.integtests.tooling.r214

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject

@ToolingApiVersion(">=2.14 <=3.0")
@TargetGradleVersion(">=1.2 <4.0")
class ToolingApiIdeaProjectDependenciesCrossVersionSpec extends ToolingApiSpecification {

    def "provides module identifiers for module dependencies"() {
        projectDir.file('settings.gradle').text = '''
include "a", "a:b"
rootProject.name = 'root'
'''
        projectDir.file('build.gradle').text = '''
allprojects {
    apply plugin: 'java'
}
project(':a') {
    dependencies {
        compile project(':')
        compile project(':a:b')
    }
}
'''

        when:
        IdeaProject ideaProject = loadToolingModel(IdeaProject)

        then:
        IdeaModule ideaModuleRoot = ideaProject.modules.find { it.gradleProject.path == ":" }
        IdeaModule ideaModuleA = ideaProject.modules.find { it.gradleProject.path == ":a" }
        IdeaModule ideaModuleB = ideaProject.modules.find { it.gradleProject.path == ":a:b" }

        ideaModuleA.dependencies.size() == 2

        IdeaModuleDependency rootDependency = ideaModuleA.dependencies.find { it.dependencyModule.name == 'root' }
        rootDependency != null
        rootDependency.target == ideaModuleRoot.identifier

        IdeaModuleDependency otherDependency = ideaModuleA.dependencies.find { it.dependencyModule.name == 'b' }
        otherDependency != null
        otherDependency.target == ideaModuleB.identifier
    }
}
