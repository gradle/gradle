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
import org.gradle.tooling.internal.protocol.DefaultIdeaModuleIdentifier
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaModuleIdentifier
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.util.GradleVersion

@ToolingApiVersion(">=2.14")
@TargetGradleVersion(">=1.0")
class ToolingApiIdeaProjectDependenciesCrossVersionSpec extends ToolingApiSpecification {

    def "can build the idea project dependencies for a java project"() {
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
        IdeaModule ideaModule = ideaProject.children[0]

        ideaModule.dependencies.size() == 2

        IdeaModuleDependency rootDependency = ideaModule.dependencies.find { it.dependencyModule.name == 'root' }
        rootDependency != null
        rootDependency.target == getModuleIdentifier(":", projectDir)

        IdeaModuleDependency otherDependency = ideaModule.dependencies.find { it.dependencyModule.name == 'b' }
        otherDependency != null
        otherDependency.target == getModuleIdentifier(":a:b", projectDir.file("a", "b"))
    }

    private IdeaModuleIdentifier getModuleIdentifier(String path, File rootDir) {
        if (GradleVersion.version(getTargetDist().version.baseVersion.version) >= GradleVersion.version("2.14")) {
            return new DefaultIdeaModuleIdentifier(rootDir);
        }
        return new DefaultIdeaModuleIdentifier(path);
    }
}
