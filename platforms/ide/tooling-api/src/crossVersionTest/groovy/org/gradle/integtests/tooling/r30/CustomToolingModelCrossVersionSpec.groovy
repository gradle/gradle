/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r30

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.r16.CustomModel

class CustomToolingModelCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        file('build.gradle') << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

allprojects {
    apply plugin: CustomPlugin
}

class CustomModel implements Serializable {
    static final INSTANCE = new CustomThing()
    String getValue() { 'greetings' }
    CustomThing getThing() { return INSTANCE }
    Set<CustomThing> getThings() { return [INSTANCE] }
    Map<String, CustomThing> getThingsByName() { return [child: INSTANCE] }
    CustomThing findThing(String name) { return INSTANCE }
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
"""
    }

    def "retains underlying object identity in model returned to client"() {
        when:
        def model = withConnection { connection ->
            connection.model(CustomModel).get()
        }

        then:
        model.thing.is(model.thing)
        model.things[0].is(model.thing)
        model.thingsByName.child.is(model.thing)
        model.findThing("child").is(model.thing)
    }

    def "retains underlying object identity in model returned to client via build action"() {
        settingsFile << "include 'a', 'b'"

        when:
        CustomModel model = withConnection { connection ->
            connection.action(new CustomModelBuildingAction()).run()
        }

        then:
        model.thing.is(model.thing)
        model.things[0].is(model.thing)
    }

    def "retains underlying object identity in complex model returned to client via build action"() {
        settingsFile << "include 'a', 'b'"

        when:
        Map<String, CustomModel> model = withConnection { connection ->
            connection.action(new ComplexCustomModelBuildingAction()).run()
        }

        then:
        model[':a'].thing.is(model[':b'].thing)
    }
}
