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
import groovy.transform.EqualsAndHashCode
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

import static com.google.common.base.Preconditions.checkNotNull

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
        visitor.roots == ["null::$myTask"] as List<String>
        visitor.nested == ["nested::null", "nestedList::null", "nestedListOfLists::null", "nestedMap::null", "nestedNamedList::null", "nestedProperty::null"] as List<String>
        visitor.leaves == ["inputProperty::null"] as List<String>
    }

    def "type walker should visit all nested nodes"() {
        when:
        def visitor = new TestStaticMetadataVisitor()
        TypeMetadataWalker.typeWalker(typeMetadataStore, TestNested.class).walk(TypeToken.of(MyTask), visitor)

        then:
        visitor.roots == ["null::MyTask"]
        visitor.nested ==~ [
            "nested::NestedType",
            "nestedList.*::NestedType",
            "nestedListOfLists.*.*::NestedType",
            "nestedMap.<key>::NestedType",
            "nestedNamedList.<name>::NamedType",
            "nestedProperty::NestedType"
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
            "nested", "nested.secondNested", "nested.secondNested.thirdNested",
            "nestedList.*", "nestedList.*.secondNested", "nestedList.*.secondNested.thirdNested",
            "nestedListOfLists.*.*", "nestedListOfLists.*.*.secondNested", "nestedListOfLists.*.*.secondNested.thirdNested",
            "nestedMap.<key>", "nestedMap.<key>.secondNested", "nestedMap.<key>.secondNested.thirdNested",
            "nestedProperty", "nestedProperty.secondNested", "nestedProperty.secondNested.thirdNested",
        ]
    }

    def "instance walker should throw exception when detecting nested cycles for '#propertyWithCycle' property"() {
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

    def "instance walker should visit nested properties unpack errors for #propertyType"() {
        given:
        Supplier<Object> propertyValue = propertyValueSupplier as Supplier<Object>
        def instance = new Object() {
            @TestNested
            Object getNested() {
                return propertyValue.get()
            }
        }
        Map<String, Throwable> errors = [:]
        def visitor = new TestInstanceMetadataVisitor() {
            @Override
            void visitNestedUnpackingError(String qualifiedName, Exception e) {
                errors[qualifiedName] = e
            }
        }

        when:
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(instance, visitor)

        then:
        errors['nested'] instanceof RuntimeException
        errors['nested'].message == "Boom for $propertyType"

        where:
        propertyType          | propertyValueSupplier
        "plain Java property" | { throw new RuntimeException("Boom for plain Java property") }
        "Provider property"   | { TestUtil.providerFactory().provider { throw new RuntimeException("Boom for Provider property") } }
    }

    def "instance walker should not allow null for nested #descriptionSuffix"() {
        given:
        def propertyValue = value
        def instance = new Object() {
            @TestNested
            Object getNested() {
                return propertyValue
            }
        }
        def visitor = new TestInstanceMetadataVisitor()

        when:
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(instance, visitor)

        then:
        def exception = thrown(exceptionType)
        exception.message == exceptionMessage

        where:
        descriptionSuffix         | value                                                                   | exceptionType         | exceptionMessage
        "map values"              | ["key1": "Hello", "key2": null]                                         | IllegalStateException | "Null value is not allowed for the nested collection property 'nested.key2'"
        "map keys"                | ["key1": "Hello", (null): "Hello"]                                      | NullPointerException  | "Null keys in nested map 'nested' are not allowed."
        "iterable values"         | ["hello", null]                                                         | IllegalStateException | "Null value is not allowed for the nested collection property 'nested.\$1'"
        "map provider value"      | ["key1": "Hello", "key2": TestUtil.providerFactory().provider { null }] | IllegalStateException | "Null value is not allowed for the nested collection property 'nested.key2'"
        "iterable provider value" | ["hello", TestUtil.providerFactory().provider { null }]                 | IllegalStateException | "Null value is not allowed for the nested collection property 'nested.\$1'"
    }

    def "instance walker should allow visiting null nested values"() {
        given:
        def instance = new Object() {
            @TestNested
            NestedType getNested() {
                return null
            }
            @TestNested
            Object getNestedObject() {
                return null
            }
            @TestNested
            List<?> getNestedList() {
                return null
            }
            @TestNested
            Map<?, ?> getNestedMap() {
                return null
            }
            @TestNested
            Provider<Provider<Provider<NestedType>>> getNestedProvider() {
                return null
            }
            @TestNested
            Provider<Provider<Provider<?>>> getNestedGenericProvider() {
                return null
            }
        }
        def visitor = new TestInstanceMetadataVisitor()

        when:
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(instance, visitor)

        then:
        visitor.getNested("nested") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(NestedType.class)), "nested", "null")
        visitor.getNested("nestedObject") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(Object.class)), "nestedObject", "null")
        visitor.getNested("nestedList") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(List.class)), "nestedList", "null")
        visitor.getNested("nestedMap") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(Map.class)), "nestedMap", "null")
        visitor.getNested("nestedProvider") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(NestedType.class)), "nestedProvider", "null")
        visitor.getNested("nestedGenericProvider") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(Object.class)), "nestedGenericProvider", "null")
    }

    def "instance walker should allow visiting null nested values for providers with null values"() {
        given:
        def instance = new Object() {
            @TestNested
            Provider<NestedType> getNested() {
                return TestUtil.providerFactory().provider { null } as Provider<NestedType>
            }
            @TestNested
            Object getNestedObject() {
                return TestUtil.providerFactory().provider { null }
            }
            @TestNested
            Provider<Provider<Provider<NestedType>>> getNestedProvider() {
                return TestUtil.providerFactory().provider { TestUtil.providerFactory().provider { null } } as Provider<Provider<Provider<NestedType>>>
            }
            @TestNested
            Provider<Provider<?>> getNestedGenericProvider() {
                return TestUtil.providerFactory().provider { null } as Provider<Provider<?>>
            }
        }
        def visitor = new TestInstanceMetadataVisitor()

        when:
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(instance, visitor)

        then:
        visitor.getNested("nested") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(NestedType.class)), "nested", "null")
        visitor.getNested("nestedObject") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(Object.class)), "nestedObject", "null")
        visitor.getNested("nestedProvider") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(NestedType.class)), "nestedProvider", "null")
        visitor.getNested("nestedGenericProvider") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(Object.class)), "nestedGenericProvider", "null")
    }

    def "instance walker should allow visiting null nested Provider values"() {
        given:
        def instance = new Object() {
            @TestNested
            NestedType getNested() {
                return null
            }
            @TestNested
            Object getNestedObject() {
                return null
            }
            @TestNested
            List<?> getNestedList() {
                return null
            }
            @TestNested
            Map<?, ?> getNestedMap() {
                return null
            }
            @TestNested
            Provider<Provider<Provider<NestedType>>> getNestedProvider() {
                return null
            }
            @TestNested
            Provider<Provider<Provider<?>>> getNestedGenericProvider() {
                return null
            }
        }
        def visitor = new TestInstanceMetadataVisitor()

        when:
        TypeMetadataWalker.instanceWalker(typeMetadataStore, TestNested.class).walk(instance, visitor)

        then:
        visitor.getNested("nested") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(NestedType.class)), "nested", "null")
        visitor.getNested("nestedObject") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(Object.class)), "nestedObject", "null")
        visitor.getNested("nestedList") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(List.class)), "nestedList", "null")
        visitor.getNested("nestedMap") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(Map.class)), "nestedMap", "null")
        visitor.getNested("nestedProvider") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(NestedType.class)), "nestedProvider", "null")
        visitor.getNested("nestedGenericProvider") == new CollectedNode(checkNotNull(typeMetadataStore.getTypeMetadata(Object.class)), "nestedGenericProvider", "null")
    }

    static String normalizeToString(String toString) {
        return toString.replace("$AbstractTypeMetadataWalkerTest.class.name\$", "")
    }

    static class TestStaticMetadataVisitor extends TestNodeMetadataVisitor<TypeToken<?>> implements TypeMetadataWalker.StaticMetadataVisitor {
        @Override
        protected String valueToString(TypeToken<?> value) {
            return value.toString()
        }
    }

    static class TestInstanceMetadataVisitor extends TestNodeMetadataVisitor<Object> implements TypeMetadataWalker.InstanceMetadataVisitor {
        private final List<CollectedNode> leaves = []

        @Override
        void visitNestedUnpackingError(String qualifiedName, Exception e) {
            throw e
        }

        @Override
        protected String valueToString(Object value) {
            if (value instanceof Property<?>) {
                return "Property[" + value.get() + "]"
            } else if (value instanceof Provider<?>) {
                return "Provider[" + value.get() + "]"
            } else {
                return Objects.toString(value)
            }
        }


        @Override
        void visitLeaf(Object parent, String qualifiedName, PropertyMetadata propertyMetadata) {
            def value = valueToString(propertyMetadata.getPropertyValue(parent))
            def node = new CollectedNode(null, qualifiedName, value)
            addNode(node)
            leaves.add(node)
        }

        List<String> getLeaves() {
            return leaves.collect { it.toString() }
        }
    }

    static abstract class TestNodeMetadataVisitor<T> implements TypeMetadataWalker.TypeMetadataVisitor<T> {
        private final List<CollectedNode> all = []
        private final List<CollectedNode> roots = []
        private final List<CollectedNode> nested = []

        protected void addNode(CollectedNode node) {
            all.add(node)
        }

        @Override
        void visitRoot(TypeMetadata typeMetadata, T value) {
            def node = new CollectedNode(typeMetadata, null, String.valueOf(value))
            addNode(node)
            roots.add(node)
        }

        @Override
        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, T value) {
            def node = new CollectedNode(typeMetadata, qualifiedName, String.valueOf(value))
            addNode(node)
            nested.add(node)
        }

        abstract protected String valueToString(T value)

        List<String> getAll() {
            return all.collect { it.toString() }
        }

        List<String> getRoots() {
            return roots.collect { it.toString() }
        }

        List<String> getNested() {
            return nested.collect { it.toString() }
        }

        CollectedNode getNested(String name) {
            return nested.find { it.qualifiedName == name }
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

    @EqualsAndHashCode
    static class CollectedNode {
        TypeMetadata typeMetadata
        String qualifiedName
        String toString

        CollectedNode(TypeMetadata typeMetadata, String qualifiedName, String toString = null) {
            this.typeMetadata = typeMetadata
            this.qualifiedName = qualifiedName
            this.toString = toString
        }

        @Override
        String toString() {
            return normalizeToString(toString == null ? qualifiedName : "$qualifiedName::$toString")
        }
    }
}
