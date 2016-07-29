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
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.r16.CustomModel

class CustomToolingModelCrossVersionSpec extends ToolingApiSpecification {
    @ToolingApiVersion(">=3.0")
    def "retains underlying graph in model returned to client"() {
        file('build.gradle') << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

apply plugin: CustomPlugin

class CustomModel implements Serializable {
    final child1 = new CustomThing()
    String getValue() { 'greetings' }
    CustomThing getThing() { return child1 }
    Set<CustomThing> getThings() { return [child1] }
    Map<String, CustomThing> getThingsByName() { return [child: child1] }
    CustomThing findThing(String name) { return child1 }
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
}
