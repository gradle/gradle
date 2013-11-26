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

package org.gradle.model.internal

import org.gradle.api.Action
import org.gradle.model.ModelFinalizer
import org.gradle.model.ModelRule
import spock.lang.Specification

class ModelRegistryBackedModelRulesTest extends Specification {

    static class ModelElement {
        List<String> names = []
    }

    static class DerivedThing {
        String name
    }

    def modelRegistry = new DefaultModelRegistry()
    def rules = new ModelRegistryBackedModelRules(modelRegistry)

    def "can configure by rules"() {
        when:
        rules.register("element", new ModelElement())
        rules.register("things", [] as List<DerivedThing>)

        3.times { int i ->
            rules.rule(new ModelRule() {
                void addName(ModelElement modelElement) {
                    modelElement.names << "name$i"
                }
            })
        }

        rules.rule(new ModelRule() {
            public void registerThings(List<DerivedThing> things, ModelElement element) {
                element.names.each {
                    things << new DerivedThing(name: it)
                }
            }
        })

        then:
        List<DerivedThing> things = modelRegistry.get("things", List)
        things*.name == ["name0", "name1", "name2"]
    }

    def "can configure by action"() {
        when:
        rules.register("element", new ModelElement())

        3.times { int i ->
            rules.config("element", { ModelElement it ->
                    it.names << "name$i"
            } as Action)
        }


        def element = modelRegistry.get("element", ModelElement)
        then:
        element
        element.names == ["name0", "name1", "name2"]
    }

    def "can finalize"() {
        when:
        rules.register("element", new ModelElement())

        rules.rule(new ModelFinalizer() {
            void addFinal(ModelElement modelElement) {
                modelElement.names << "final"
            }
        })

        3.times { int i ->
            rules.config("element", { ModelElement it ->
                it.names << "name$i"
            } as Action)
        }


        def element = modelRegistry.get("element", ModelElement)
        then:
        element
        element.names == ["name0", "name1", "name2", "final"]
    }
}
