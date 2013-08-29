/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.tooling.r18

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.GradleBuild
import org.gradle.integtests.tooling.r16.CustomModel

@ToolingApiVersion(">=1.8")
class GradleBuildModelCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        file('settings.gradle') << '''
include 'a'
include 'b'
include 'b:c'
rootProject.name = 'test'
'''
        buildFile << """
allprojects {
    description = "project \$name"
    apply plugin: CustomPlugin
    task buildStuff
}

import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

class CustomModel implements Serializable {
    String value
}
class CustomBuilder implements ToolingModelBuilder {
    boolean canBuild(String modelName) {
        return modelName == 'org.gradle.integtests.tooling.r16.CustomModel'
    }
    Object buildAll(String modelName, Project project) {
        return new CustomModel(value: project.path)
    }
}
class CustomPlugin implements Plugin<Project> {
    @Inject
    CustomPlugin(ToolingModelBuilderRegistry registry) {
        registry.register(new CustomBuilder())
    }

    public void apply(Project project) {
    }
}
"""
    }

    @TargetGradleVersion(">=1.8")
    def "can request models from various projects"() {
        when:
        Map<String, CustomModel> result = withConnection { connection -> connection.action(new MultiProjectAction()).run() }

        then:
        result != null
        result.keySet() == ['test', 'a', 'b', 'c'] as Set
        result.values()*.value as Set == [':', ':a', ':b', ':b:c'] as Set
    }

    // TODO:ADAM - make this work for all target versions
    @TargetGradleVersion(">=1.8")
    def "can request GradleBuild model"() {
        when:
        GradleBuild model = withConnection { connection -> connection.getModel(GradleBuild) }

        then:
        model.rootProject.name == 'test'
        model.rootProject.children.size() == 2
        model.rootProject.children.every { it.parent == model.rootProject }
        model.projects*.name == ['test', 'a', 'b', 'c']
    }
}
