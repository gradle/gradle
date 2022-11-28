/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.properties.annotations

import com.google.common.reflect.TypeToken
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.provider.Property
import org.gradle.internal.reflect.annotations.Ignored
import org.gradle.internal.reflect.annotations.Long
import org.gradle.internal.reflect.annotations.TestAnnotationHandlingSupport
import org.gradle.internal.reflect.annotations.TestNested
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.function.Supplier

class AbstractTypeMetadataWalkerTest extends Specification implements TestAnnotationHandlingSupport {

    def "should walk a type"() {
        when:
        List<CollectedInput> inputs = []
        TypeMetadataWalker.typeWalker(typeMetadataStore, TestNested.class).walk(TypeToken.of(MyTask), new TypeMetadataWalker.NodeMetadataVisitor<TypeToken<?>>() {
            @Override
            void visitRoot(TypeMetadata typeMetadata, TypeToken<?> value) {
                inputs.add(new CollectedInput(null, value))
            }

            @Override
            void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, TypeToken<?> value) {
                inputs.add(new CollectedInput(qualifiedName, value))
            }

            @Override
            void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<TypeToken<?>> value) {
                inputs.add(new CollectedInput(qualifiedName, value.get()))
            }
        })

        then:
        inputs.collect { it.qualifiedName } == [
            null,
            "inputProperty",
            "nested", "nested.inputProperty",
            "nestedList.*", "nestedList.*.inputProperty",
            "nestedListOfLists.*.*", "nestedListOfLists.*.*.inputProperty",
            "nestedMap.<key>", "nestedMap.<key>.inputProperty",
            "nestedNamedList.<name>", "nestedNamedList.<name>.inputProperty",
            "nestedProperty", "nestedProperty.inputProperty"
        ]
    }

    def "should walk type instance"() {
        given:
        def firstProperty = TestUtil.propertyFactory().property(String).value("first-property")
        def secondProperty = TestUtil.propertyFactory().property(String).value("second-property")
        def thirdProperty = TestUtil.propertyFactory().property(String).value("third-property")
        def myType = new MyTask()
        def nestedType = new NestedType()
        def namedType = new NamedType()
        nestedType.inputProperty = secondProperty
        namedType.inputProperty = thirdProperty
        myType.inputProperty = firstProperty
        myType.nested = nestedType
        myType.nestedList = [nestedType, nestedType]
        myType.nestedMap = ["key1": nestedType, "key2": nestedType]
        myType.nestedListOfLists = [[nestedType]]
        myType.nestedNamedList = [namedType]
        myType.nestedProperty = TestUtil.propertyFactory().property(NestedType).value(nestedType)

        when:
        Map<String, CollectedInput> inputs = [:]
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(myType, new TypeMetadataWalker.NodeMetadataVisitor<Object>() {
            @Override
            void visitRoot(TypeMetadata typeMetadata, Object value) {
                inputs[null] = new CollectedInput(null, value)
            }

            @Override
            void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, Object value) {
                assert !inputs.containsKey(qualifiedName)
                inputs[qualifiedName] = new CollectedInput(qualifiedName, value)
            }

            @Override
            void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<Object> value) {
                assert !inputs.containsKey(qualifiedName)
                inputs[qualifiedName] = new CollectedInput(qualifiedName, value.get())
            }
        })

        then:
        inputs[null].value == myType
        inputs["inputProperty"].value == firstProperty
        inputs["nested"].value == nestedType
        inputs["nested.inputProperty"].value == secondProperty
        inputs["nestedList.\$1"].value == nestedType
        inputs["nestedList.\$2"].value == nestedType
        inputs["nestedList.\$1.inputProperty"].value == secondProperty
        inputs["nestedList.\$2.inputProperty"].value == secondProperty
        inputs["nestedMap.key1"].value == nestedType
        inputs["nestedMap.key2"].value == nestedType
        inputs["nestedMap.key1.inputProperty"].value == secondProperty
        inputs["nestedMap.key2.inputProperty"].value == secondProperty
        inputs["nestedListOfLists.\$1.\$1"].value == nestedType
        inputs["nestedListOfLists.\$1.\$1.inputProperty"].value == secondProperty
        inputs["nestedNamedList.\$1"].value == namedType
        inputs["nestedNamedList.\$1.inputProperty"].value == thirdProperty
        inputs["nestedProperty"].value == nestedType
        inputs["nestedProperty.inputProperty"].value == secondProperty
    }

    def "type walker should handle types with nested cycles"() {
        when:
        List<CollectedInput> inputs = []
        TypeMetadataWalker.typeWalker(typeMetadataStore, TestNested.class).walk(TypeToken.of(MyCycleTask), new TypeMetadataWalker.NodeMetadataVisitor<TypeToken<?>>() {
            @Override
            void visitRoot(TypeMetadata typeMetadata, TypeToken<?> value) {
                inputs.add(new CollectedInput(null, value))
            }

            @Override
            void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, TypeToken<?> value) {
                inputs.add(new CollectedInput(qualifiedName, value))
            }

            @Override
            void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<TypeToken<?>> value) {
                inputs.add(new CollectedInput(qualifiedName, value.get()))
            }
        })

        then:
        inputs.collect { it.qualifiedName } == [
            null,
            "nested", "nested.secondNested", "nested.secondNested.thirdNested", "nested.secondNested.thirdNested.input",
            "nestedList.*", "nestedList.*.secondNested", "nestedList.*.secondNested.thirdNested", "nestedList.*.secondNested.thirdNested.input",
            "nestedListOfLists.*.*", "nestedListOfLists.*.*.secondNested", "nestedListOfLists.*.*.secondNested.thirdNested", "nestedListOfLists.*.*.secondNested.thirdNested.input",
            "nestedMap.<key>", "nestedMap.<key>.secondNested", "nestedMap.<key>.secondNested.thirdNested", "nestedMap.<key>.secondNested.thirdNested.input",
            "nestedProperty", "nestedProperty.secondNested", "nestedProperty.secondNested.thirdNested", "nestedProperty.secondNested.thirdNested.input",
        ]
    }

    def "instance walker should handle instances with nested cycles for '#propertyWithCycle' property"() {
        given:
        def instance = new MyCycleTask()
        instance[propertyWithCycle] = propertyValue

        when:
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(instance, new TypeMetadataWalker.NodeMetadataVisitor<Object>() {
            @Override
            void visitRoot(TypeMetadata typeMetadata, Object value) {
            }

            @Override
            void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, Object value) {
            }

            @Override
            void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<Object> value) {
            }
        })

        then:
        def exception = thrown(GradleException)
        exception.message == "Cycles between nested beans are not allowed. Cycle detected between: $expectedCycle."

        where:
        propertyWithCycle   | propertyValue                                                                                   | expectedCycle
        'nested'            | CycleFirstNode.newInitializedCycle()                                                            | "'nested' and 'nested.secondNested.thirdNested.fourthNested'"
        'nestedProperty'    | TestUtil.propertyFactory().property(CycleFirstNode).value(CycleFirstNode.newInitializedCycle()) | "'nestedProperty' and 'nestedProperty.secondNested.thirdNested.fourthNested'"
        'nestedList'        | [CycleFirstNode.newInitializedCycle()]                                                          | "'nestedList.\$1' and 'nestedList.\$1.secondNested.thirdNested.fourthNested'"
        'nestedMap'         | ['key1': CycleFirstNode.newInitializedCycle()]                                                  | "'nestedMap.key1' and 'nestedMap.key1.secondNested.thirdNested.fourthNested'"
        'nestedListOfLists' | [[CycleFirstNode.newInitializedCycle()]]                                                        | "'nestedListOfLists.\$1.\$1' and 'nestedListOfLists.\$1.\$1.secondNested.thirdNested.fourthNested'"
    }

    static class MyTask {
        @Long
        Property<String> inputProperty
        @TestNested
        NestedType nested
        @TestNested
        List<NestedType> nestedList
        @TestNested
        Map<String, NestedType> nestedMap
        @TestNested
        List<List<NestedType>> nestedListOfLists
        @TestNested
        List<NamedType> nestedNamedList
        @TestNested
        Property<NestedType> nestedProperty
    }

    static class NestedType {
        @Long
        Property<String> inputProperty
    }

    static class NamedType implements Named {
        @Long
        Property<String> inputProperty

        @Ignored
        String getName() {
            return "namedType"
        }
    }

    static class MyCycleTask {
        @TestNested
        CycleFirstNode nested
        @TestNested
        Property<CycleFirstNode> nestedProperty
        @TestNested
        List<CycleFirstNode> nestedList
        @TestNested
        Map<String, CycleFirstNode> nestedMap
        @TestNested
        List<List<CycleFirstNode>> nestedListOfLists
    }

    static class CycleFirstNode {
        @TestNested
        CycleSecondNode secondNested

        static CycleFirstNode newInitializedCycle() {
            def cycleFirstNode = new CycleFirstNode()
            def cycleSecondNode = new CycleSecondNode()
            def cycleThirdNode = new CycleThirdNode()
            cycleFirstNode.secondNested = cycleSecondNode
            cycleSecondNode.thirdNested = cycleThirdNode
            cycleThirdNode.fourthNested = cycleFirstNode
            cycleThirdNode.input = cycleFirstNode
            return cycleFirstNode
        }
    }

    static class CycleSecondNode {
        @TestNested
        CycleThirdNode thirdNested
    }

    static class CycleThirdNode {
        @TestNested
        CycleFirstNode fourthNested
        @Long
        CycleFirstNode input
    }

    static class CollectedInput {
        String qualifiedName
        Object value

        CollectedInput(String qualifiedName, Object value) {
            this.qualifiedName = qualifiedName
            this.value = value
        }
    }
}
