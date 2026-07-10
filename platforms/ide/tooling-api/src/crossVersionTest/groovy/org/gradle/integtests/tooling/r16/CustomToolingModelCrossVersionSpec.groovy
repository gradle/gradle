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

package org.gradle.integtests.tooling.r16


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import spock.lang.Ignore

class CustomToolingModelCrossVersionSpec extends ToolingApiSpecification {
    def "plugin can contribute a custom tooling model"() {
        file("buildSrc/src/main/java/CustomModel.java").java("""
import java.io.Serializable;
import java.util.*;
public class CustomModel implements Serializable {
    public String getValue() { return "greetings"; }
    public Set<CustomThing> getThings() {
        Set<CustomThing> customThings = new HashSet<>();
        customThings.add(new CustomThing());
        return customThings;
    }
    public Map<String, CustomThing> getThingsByName() {
        Map<String, CustomThing> customThings = new HashMap<>();
        customThings.put("thing", new CustomThing());
        return customThings;
    }
}
""")

        file("buildSrc/src/main/java/CustomThing.java").java("""
import java.io.Serializable;
class CustomThing implements Serializable {
}
""")

        file('build.gradle') << """
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilder
import javax.inject.Inject

apply plugin: CustomPlugin

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
        model.value == 'greetings'
        model.things.find { it instanceof CustomModel.Thing }
        model.thingsByName.thing instanceof CustomModel.Thing
    }

    @Ignore("work in progress")
    def "gives reasonable error message when model build fails"() {
        expect: false
    }

    @Ignore("work in progress")
    def "gives reasonable error message when model cannot be transported to consumer"() {
        expect: false
    }
}
