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

package org.gradle.model.internal.inspect

import org.gradle.model.InvalidModelRuleDeclarationException
import org.gradle.model.Model
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.RuleSource
import org.gradle.model.internal.core.ModelReference
import org.gradle.model.internal.core.ModelState
import org.gradle.model.internal.core.ModelType
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.registry.ModelRegistry
import spock.lang.Specification
import spock.lang.Unroll

class ModelRuleInspectorTest extends Specification {

    ModelRegistry registry = new DefaultModelRegistry()
    def registryMock = Mock(ModelRegistry)
    def inspector = new ModelRuleInspector()

    static class ModelThing {
        final String name

        ModelThing(String name) {
            this.name = name
        }
    }

    static class EmptyClass {}

    def "can inspect class with no rules"() {
        when:
        inspector.inspect(EmptyClass, registryMock)

        then:
        0 * registryMock._
    }

    static class SimpleModelCreationRuleInferredName {
        @Model
        static ModelThing modelPath() {
            new ModelThing("foo")
        }
    }

    def "can inspect class with simple model creation rule"() {
        when:
        inspector.inspect(SimpleModelCreationRuleInferredName, registry)

        then:
        def state = registry.state(new ModelPath("modelPath"))
        state.status == ModelState.Status.PENDING

        def element = registry.get("modelPath", ModelThing)
        element.name == "foo"
    }

    static class HasOneSource {
        @RuleSource
        static class Source {}

        static class NotSource {}
    }

    static class HasTwoSources {
        @RuleSource
        static class SourceOne {}

        @RuleSource
        static class SourceTwo {}

        static class NotSource {}
    }

    @Unroll
    def "find model rule sources - #clazz"() {
        expect:
        new ModelRuleInspector().getDeclaredSources(clazz) == expected.toSet()

        where:
        clazz         | expected
        String        | []
        HasOneSource  | [HasOneSource.Source]
        HasTwoSources | [HasTwoSources.SourceOne, HasTwoSources.SourceTwo]
    }

    static class HasGenericModelRule {
        @Model
        static <T> List<T> thing() {
            []
        }
    }

    def "model creation rule cannot be generic"() {
        when:
        inspector.inspect(HasGenericModelRule, registry)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == "$HasGenericModelRule.name#thing() is not a valid model creation rule: cannot have type variables (i.e. cannot be a generic method)"
    }

    static class ConcreteGenericModelType {
        @Model
        static List<String> strings() {
            []
        }
    }

    def "type variables of model type are captured"() {
        when:
        inspector.inspect(ConcreteGenericModelType, registry)
        def element = registry.element(new ModelReference("strings", new ModelType(List)))
        def type = element.reference.type


        then:
        type.parameterized
        type.typeVariables[0] == new ModelType(String)
    }

    static interface HasStrings<T> {
        List<T> strings()
    }

    static class ConcreteGenericModelTypeImplementingGenericInterface implements HasStrings<String> {
        @Model
        List<String> strings() {
            []
        }
    }

    def "type variables of model type are captured when method is generic in interface"() {
        when:
        inspector.inspect(ConcreteGenericModelTypeImplementingGenericInterface, registry)
        def element = registry.element(new ModelReference("strings", new ModelType(List)))
        def type = element.reference.type

        then:
        type.parameterized
        type.typeVariables[0] == new ModelType(String)
    }

}
