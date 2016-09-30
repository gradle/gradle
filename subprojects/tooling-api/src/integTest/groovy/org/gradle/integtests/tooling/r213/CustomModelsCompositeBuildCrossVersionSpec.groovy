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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.tooling.fixture.MultiModelToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.r16.CustomModel

/**
 * Tooling client requests custom model type for every project in a composite
 */
class CustomModelsCompositeBuildCrossVersionSpec extends MultiModelToolingApiSpecification {

    @TargetGradleVersion(">=1.2 <1.6")
    def "decent error message for Gradle version that doesn't support custom models"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])

        when:
        def modelResults = getModels(CustomModel)

        then:
        modelResults.size() == 1
        modelResults.each {
            def e = it.failure
            assert e.message.contains('does not support building a model of type \'CustomModel\'.')
            assert e.message.contains('Support for building custom tooling models was added in Gradle 1.6 and is available in all later versions.')
        }
    }

    @TargetGradleVersion(">=1.6")
    def "decent error message for unknown custom model"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])

        when:
        def modelResults = getModels(CustomModel)

        then:
        modelResults.size() == 1
        modelResults.each {
            def e = it.failure
            assert e.message == "No model of type 'CustomModel' is available in this build."
        }
    }

    @TargetGradleVersion(">=1.6")
    def "can retrieve custom models for root projects in composite"() {
        setup:
        multiProjectBuildInRootFolder("B", ['x', 'y'])
        buildFile << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

class CustomModel implements Serializable {
    String getValue() { 'greetings' }
    Set<CustomThing> getThings() { return [new CustomThing()] }
    Map<String, CustomThing> getThingsByName() { return [thing: new CustomThing()] }
}
class CustomThing implements Serializable {
}
class CustomBuilder implements ToolingModelBuilder {
    boolean canBuild(String modelName) {
        return modelName == '${CustomModel.name}'
    }
    Object buildAll(String modelName, Project project) {
        return new CustomModel()
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

apply plugin: CustomPlugin
"""

        when:
        def modelResults = getModels(CustomModel)

        then:
        modelResults.size() == 1
        modelResults.each { result ->
            assert result.failure == null
            assert result.model.value == 'greetings'
            assert result.model.things.find { it instanceof CustomModel.Thing }
            assert result.model.thingsByName.thing instanceof CustomModel.Thing
        }
    }

}
