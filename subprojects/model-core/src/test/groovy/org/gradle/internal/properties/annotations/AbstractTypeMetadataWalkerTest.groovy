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
import org.gradle.api.Named
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.internal.reflect.annotations.Ignored
import org.gradle.internal.reflect.annotations.Long
import org.gradle.internal.reflect.annotations.TestAnnotationHandlingSupport
import org.gradle.internal.reflect.annotations.TestNested
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.function.Function
import java.util.function.Supplier

class AbstractTypeMetadataWalkerTest extends Specification implements TestAnnotationHandlingSupport {

    def "type walker should correctly visit empty type"() {
        given:
        def visitor = new TestStaticMetadataVisitor()

        when:
        TypeMetadataWalker.typeWalker(typeMetadataStore, TestNested.class).walk(TypeToken.of(MyEmptyTask), visitor)

        then:
        visitor.all == ["null::MyEmptyTask"]
    }

    def "instance walker should correctly visit instance with null values"() {
        given:
        def myTask = new MyTask()
        def visitor = new TestInstanceMetadataVisitor()

        when:
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(myTask, visitor)

        then:
        visitor.all == ["null::$myTask", "inputProperty::null"] as List<String>
    }

    def "type walker should visit all nested nodes and properties"() {
        when:
        def visitor = new TestStaticMetadataVisitor()
        TypeMetadataWalker.typeWalker(typeMetadataStore, TestNested.class).walk(TypeToken.of(MyTask), visitor)

        then:
        visitor.roots == ["null::MyTask"]
        visitor.nested == [
            "nested::NestedType",
            "nestedList.*::NestedType",
            "nestedListOfLists.*.*::NestedType",
            "nestedMap.<key>::NestedType",
            "nestedNamedList.<name>::NamedType",
            "nestedProperty::NestedType"
        ]
        visitor.leaves == [
            "inputProperty::org.gradle.api.provider.Property<java.lang.String>",
            "nested.inputProperty::org.gradle.api.provider.Property<java.lang.String>",
            "nestedList.*.inputProperty::org.gradle.api.provider.Property<java.lang.String>",
            "nestedListOfLists.*.*.inputProperty::org.gradle.api.provider.Property<java.lang.String>",
            "nestedMap.<key>.inputProperty::org.gradle.api.provider.Property<java.lang.String>",
            "nestedNamedList.<name>.inputProperty::org.gradle.api.provider.Property<java.lang.String>",
            "nestedProperty.inputProperty::org.gradle.api.provider.Property<java.lang.String>"
        ]
    }

    def "instance walker should visit all nested nodes and properties"() {
        given:
        def firstProperty = TestUtil.propertyFactory().property(String).value("first-property")
        def secondProperty = TestUtil.propertyFactory().property(String).value("second-property")
        def thirdProperty = TestUtil.propertyFactory().property(String).value("third-property")
        def myTask = new MyTask()
        def nestedType = new NestedType()
        def namedType = new NamedType()
        nestedType.inputProperty = secondProperty
        namedType.inputProperty = thirdProperty
        myTask.inputProperty = firstProperty
        myTask.nested = nestedType
        myTask.nestedList = [nestedType, nestedType]
        myTask.nestedMap = ["key1": nestedType, "key2": nestedType]
        myTask.nestedListOfLists = [[nestedType]]
        myTask.nestedNamedList = [namedType]
        myTask.nestedProperty = TestUtil.propertyFactory().property(NestedType).value(nestedType)
        def visitor = new TestInstanceMetadataVisitor()

        when:
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(myTask, visitor)

        then:
        visitor.roots == ["null::$myTask"] as List<String>
        visitor.nested == [
            "nested::$nestedType",
            "nestedList.\$0::$nestedType",
            "nestedList.\$1::$nestedType",
            "nestedListOfLists.\$0.\$0::$nestedType",
            "nestedMap.key1::$nestedType",
            "nestedMap.key2::$nestedType",
            "nestedNamedList.namedType\$0::$namedType",
            "nestedProperty::$nestedType"
        ] as List<String>
        visitor.leaves == [
            "inputProperty::Property[first-property]",
            "nested.inputProperty::Property[second-property]",
            "nestedList.\$0.inputProperty::Property[second-property]",
            "nestedList.\$1.inputProperty::Property[second-property]",
            "nestedListOfLists.\$0.\$0.inputProperty::Property[second-property]",
            "nestedMap.key1.inputProperty::Property[second-property]",
            "nestedMap.key2.inputProperty::Property[second-property]",
            "nestedNamedList.namedType\$0.inputProperty::Property[third-property]",
            "nestedProperty.inputProperty::Property[second-property]"
        ]
    }

    def "type walker should handle types with nested cycles"() {
        when:
        def visitor = new TestStaticMetadataVisitor()
        TypeMetadataWalker.typeWalker(typeMetadataStore, TestNested.class).walk(TypeToken.of(MyCycleTask), visitor)

        then:
        visitor.allQualifiedNames == [
            null,
            "nested", "nested.secondNested", "nested.secondNested.thirdNested", "nested.secondNested.thirdNested.input",
            "nestedList.*", "nestedList.*.secondNested", "nestedList.*.secondNested.thirdNested", "nestedList.*.secondNested.thirdNested.input",
            "nestedListOfLists.*.*", "nestedListOfLists.*.*.secondNested", "nestedListOfLists.*.*.secondNested.thirdNested", "nestedListOfLists.*.*.secondNested.thirdNested.input",
            "nestedMap.<key>", "nestedMap.<key>.secondNested", "nestedMap.<key>.secondNested.thirdNested", "nestedMap.<key>.secondNested.thirdNested.input",
            "nestedProperty", "nestedProperty.secondNested", "nestedProperty.secondNested.thirdNested", "nestedProperty.secondNested.thirdNested.input",
        ]
    }

    def "instance walker should throw exception when nested cycles for '#propertyWithCycle' property"() {
        given:
        def instance = new MyCycleTask()
        instance[propertyWithCycle] = propertyValue
        def visitor = new TestInstanceMetadataVisitor()

        when:
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(instance, visitor)

        then:
        def exception = thrown(IllegalStateException)
        exception.message == "Cycles between nested beans are not allowed. Cycle detected between: $expectedCycle."

        where:
        propertyWithCycle   | propertyValue                                                                                   | expectedCycle
        'nested'            | CycleFirstNode.newInitializedCycle()                                                            | "'nested' and 'nested.secondNested.thirdNested.fourthNested'"
        'nestedProperty'    | TestUtil.propertyFactory().property(CycleFirstNode).value(CycleFirstNode.newInitializedCycle()) | "'nestedProperty' and 'nestedProperty.secondNested.thirdNested.fourthNested'"
        'nestedList'        | [CycleFirstNode.newInitializedCycle()]                                                          | "'nestedList.\$0' and 'nestedList.\$0.secondNested.thirdNested.fourthNested'"
        'nestedMap'         | ['key1': CycleFirstNode.newInitializedCycle()]                                                  | "'nestedMap.key1' and 'nestedMap.key1.secondNested.thirdNested.fourthNested'"
        'nestedListOfLists' | [[CycleFirstNode.newInitializedCycle()]]                                                        | "'nestedListOfLists.\$0.\$0' and 'nestedListOfLists.\$0.\$0.secondNested.thirdNested.fourthNested'"
    }

    def "instance walker should throw exception when nested cycles for root and '#propertyWithCycle' property"() {
        given:
        def instance = new MyCycleTask()
        instance[propertyWithCycle] = (propertyValueFunction as Function<MyCycleTask, Object>).apply(instance)
        def visitor = new TestInstanceMetadataVisitor()

        when:
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(instance, visitor)

        then:
        def exception = thrown(IllegalStateException)
        exception.message == "Cycles between nested beans are not allowed. Cycle detected between: $expectedCycle."

        where:
        propertyWithCycle   | propertyValueFunction                                                                                                           | expectedCycle
        'nested'            | { MyCycleTask root -> CycleFirstNode.newInitializedRootCycle(root) }                                                            | "'<root>' and 'nested.secondNested.thirdNested.rootNested'"
        'nestedProperty'    | { MyCycleTask root -> TestUtil.propertyFactory().property(CycleFirstNode).value(CycleFirstNode.newInitializedRootCycle(root)) } | "'<root>' and 'nestedProperty.secondNested.thirdNested.rootNested'"
        'nestedList'        | { MyCycleTask root -> [CycleFirstNode.newInitializedRootCycle(root)] }                                                          | "'<root>' and 'nestedList.\$0.secondNested.thirdNested.rootNested'"
        'nestedMap'         | { MyCycleTask root -> ['key1': CycleFirstNode.newInitializedRootCycle(root)] }                                                  | "'<root>' and 'nestedMap.key1.secondNested.thirdNested.rootNested'"
        'nestedListOfLists' | { MyCycleTask root -> [[CycleFirstNode.newInitializedRootCycle(root)]] }                                                        | "'<root>' and 'nestedListOfLists.\$0.\$0.secondNested.thirdNested.rootNested'"
    }

    static String normalizeToString(String toString) {
        return toString.replace("$AbstractTypeMetadataWalkerTest.class.name\$", "")
    }

    static class TestStaticMetadataVisitor extends TestNodeMetadataVisitor<TypeToken<?>> implements TypeMetadataWalker.StaticMetadataVisitor {
    }

    static class TestInstanceMetadataVisitor extends TestNodeMetadataVisitor<Object> implements TypeMetadataWalker.InstanceMetadataVisitor {
        @Override
        void visitMissingNested(String qualifiedName, PropertyMetadata propertyMetadata) {
        }

        @Override
        void visitUnpackNestedError(String qualifiedName, Exception e) {
        }
    }

    static abstract class TestNodeMetadataVisitor<T> implements TypeMetadataWalker.NodeMetadataVisitor<T> {
        private List<CollectedNode> all = []
        private List<CollectedNode> roots = []
        private List<CollectedNode> nested = []
        private List<CollectedNode> leaves = []

        @Override
        void visitRoot(TypeMetadata typeMetadata, T value) {
            def node = new CollectedNode(null, value)
            all.add(node)
            roots.add(node)
        }

        @Override
        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, T value) {
            def node = new CollectedNode(qualifiedName, value)
            all.add(node)
            nested.add(node)
        }

        @Override
        void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<T> value) {
            def node = new CollectedNode(qualifiedName, value.get())
            all.add(node)
            leaves.add(node)
        }

        List<String> getAll() {
            return all.collect { it.toString() }
        }

        List<String> getRoots() {
            return roots.collect { it.toString() }
        }

        List<String> getNested() {
            return nested.collect { it.toString() }
        }

        List<String> getLeaves() {
            return leaves.collect { it.toString() }
        }

        List<String> getAllQualifiedNames() {
            return all.collect { it.qualifiedName }
        }
    }

    interface WithNormalizedToString {
        @Override
        default String toString() {
            return normalizeToString(super.toString())
        }
    }

    static class MyEmptyTask implements WithNormalizedToString {
    }

    static class MyTask implements WithNormalizedToString {
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

    static class NestedType implements WithNormalizedToString {
        @Long
        Property<String> inputProperty
    }

    static class NamedType implements Named, WithNormalizedToString {
        @Long
        Property<String> inputProperty

        @Ignored
        String getName() {
            return "namedType"
        }
    }

    static class MyCycleTask implements WithNormalizedToString {
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

    static class CycleFirstNode implements WithNormalizedToString {
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

        static CycleFirstNode newInitializedRootCycle(MyCycleTask root) {
            def cycleFirstNode = new CycleFirstNode()
            def cycleSecondNode = new CycleSecondNode()
            def cycleThirdNode = new CycleThirdNode()
            cycleFirstNode.secondNested = cycleSecondNode
            cycleSecondNode.thirdNested = cycleThirdNode
            cycleThirdNode.rootNested = root
            cycleThirdNode.input = cycleFirstNode
            return cycleFirstNode
        }
    }

    static class CycleSecondNode implements WithNormalizedToString {
        @TestNested
        CycleThirdNode thirdNested
    }

    static class CycleThirdNode implements WithNormalizedToString {
        @TestNested
        CycleFirstNode fourthNested
        @TestNested
        MyCycleTask rootNested
        @Long
        CycleFirstNode input
    }

    static class CollectedNode {
        String qualifiedName
        Object value

        CollectedNode(String qualifiedName, Object value) {
            this.qualifiedName = qualifiedName
            this.value = value
        }

        @Override
        String toString() {
            return normalizeToString("$qualifiedName::${valueToString()}")
        }

        private String valueToString() {
            if (value instanceof Property<?>) {
                return "Property[" + value.get() + "]"
            } else if (value instanceof Provider<?>) {
                return "Provider[" + value.get() + "]"
            } else {
                return Objects.toString(value)
            }
        }
    }
}
