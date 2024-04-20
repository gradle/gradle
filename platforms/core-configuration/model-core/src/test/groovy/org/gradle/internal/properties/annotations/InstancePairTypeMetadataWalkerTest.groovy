/*
 * Copyright 2024 the original author or authors.
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

import groovy.transform.EqualsAndHashCode
import org.gradle.api.provider.Property
import org.gradle.internal.reflect.annotations.TestAnnotationHandlingSupport
import org.gradle.internal.reflect.annotations.TestNested
import org.gradle.internal.reflect.annotations.ThisIsAThing
import org.gradle.internal.reflect.annotations.Long
import org.gradle.internal.reflect.annotations.Short
import org.gradle.util.TestUtil
import org.jetbrains.annotations.Nullable
import spock.lang.Specification

class InstancePairTypeMetadataWalkerTest extends Specification implements TestAnnotationHandlingSupport {
    private static final def NULL_PAIR = instancePair(Property.class, null, null)

    def "pair walker should visit all nested nodes"() {
        given:
        def visitor = new TestVisitor()
        def left = new TestDataObject()
        def right = new TestDataObject()
        populateAllValues(left, right)

        when:
        InstancePairTypeMetadataWalker.instancePairWalker(typeMetadataStore, TestNested.class).walk(instancePair(TestDataObject, left, right), visitor)

        then:
        visitor.roots.size() == 1
        visitor.roots[0].pair.left == left
        visitor.roots[0].pair.right == right
        visitor.nested.collect { it.qualifiedName }.sort() == [ "anotherNestedThing", "nestedThing" ]
        visitor.leaves.collect { it.qualifiedName }.sort() == [ "anotherNestedThing.nestedShortThing", "longThing", "nestedThing.nestedLongThing", "shortThing" ]
        visitor.leaves.find { it.qualifiedName == "longThing" }?.pair == instancePair(Property.class, left.longThing, right.longThing)
        visitor.leaves.find { it.qualifiedName == "shortThing" }?.pair == instancePair(Property.class, left.shortThing, right.shortThing)
        visitor.leaves.find { it.qualifiedName == "nestedThing.nestedLongThing" }?.pair == instancePair(Property.class, left.nestedThing.nestedLongThing, right.nestedThing.nestedLongThing)
        visitor.leaves.find { it.qualifiedName == "anotherNestedThing.nestedShortThing" }?.pair == instancePair(Property.class, left.anotherNestedThing.nestedShortThing, right.anotherNestedThing.nestedShortThing)
    }

    def "pair walker handles empty leaf nodes"() {
        given:
        def visitor = new TestVisitor()
        def left = new TestDataObject()
        def right = new TestDataObject()
        populateTopLevelNestedValues(left, right)

        when:
        InstancePairTypeMetadataWalker.instancePairWalker(typeMetadataStore, TestNested.class).walk(instancePair(TestDataObject, left, right), visitor)

        then:
        visitor.roots.size() == 1
        visitor.nested.collect { it.qualifiedName }.sort() == [ "anotherNestedThing", "nestedThing" ]
        visitor.leaves.collect { it.qualifiedName }.sort() == [ "anotherNestedThing.nestedShortThing", "longThing", "nestedThing.nestedLongThing", "shortThing" ]
        visitor.leaves.find { it.qualifiedName == "nestedThing.nestedLongThing" }?.pair == NULL_PAIR
        visitor.leaves.find { it.qualifiedName == "anotherNestedThing.nestedShortThing" }?.pair == NULL_PAIR
    }

    def "pair walker handles empty nested nodes"() {
        given:
        def visitor = new TestVisitor()
        def left = new TestDataObject()
        def right = new TestDataObject()
        populateTopLevelValues(left, right)

        when:
        InstancePairTypeMetadataWalker.instancePairWalker(typeMetadataStore, TestNested.class).walk(instancePair(TestDataObject, left, right), visitor)

        then:
        visitor.roots.size() == 1
        visitor.nested.collect { it.qualifiedName }.sort() == [ "anotherNestedThing", "nestedThing" ]
        visitor.leaves.collect { it.qualifiedName }.sort() == [ "longThing", "shortThing" ]
    }

    def "pair walker handles cycles in nested nodes"() {
        given:
        def visitor = new TestVisitor()
        def left = new CycleDataObject()
        def right = new CycleDataObject()
        left.nestedThing = new CycleNestedThing1()
        left.anotherNestedThing = new CycleNestedThing2()
        right.nestedThing = new CycleNestedThing1()
        right.anotherNestedThing = new CycleNestedThing2()
        left.nestedThing.cycleDataObject = left.anotherNestedThing
        left.anotherNestedThing.cycleDataObject = left.nestedThing
        right.nestedThing.cycleDataObject = right.anotherNestedThing
        right.anotherNestedThing.cycleDataObject = right.nestedThing

        when:
        InstancePairTypeMetadataWalker.instancePairWalker(typeMetadataStore, TestNested.class).walk(instancePair(CycleDataObject, left, right), visitor)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cycles between nested beans are not allowed. Cycle detected between: 'anotherNestedThing' and 'anotherNestedThing.cycleDataObject.cycleDataObject'."
    }

    def "pair walker captures errors unpacking nodes"() {
        def visitor = new TestVisitor()
        def left = new ErrorDataObject()
        def right = new ErrorDataObject()

        when:
        InstancePairTypeMetadataWalker.instancePairWalker(typeMetadataStore, TestNested.class).walk(instancePair(ErrorDataObject, left, right), visitor)

        then:
        visitor.errors.size() == 1
        visitor.errors[0].message == "Error"
    }

    static void populateAllValues(TestDataObject left, TestDataObject right) {
        populateTopLevelValues(left, right)
        populateAllNestedValues(left, right)
    }

    static void populateTopLevelValues(TestDataObject left, TestDataObject right) {
        left.longThing = TestUtil.objectFactory().property(Long).value(1 as Long)
        right.longThing = TestUtil.objectFactory().property(Long).value(2 as Long)
        left.shortThing = TestUtil.objectFactory().property(Short).value(3 as Short)
        right.shortThing = TestUtil.objectFactory().property(Short).value(4 as Short)
    }

    static void populateTopLevelNestedValues(TestDataObject left, TestDataObject right) {
        left.nestedThing = new NestedThing()
        right.nestedThing = new NestedThing()
        left.anotherNestedThing = new AnotherNestedThing()
        right.anotherNestedThing = new AnotherNestedThing()
    }

    static void populateAllNestedValues(TestDataObject left, TestDataObject right) {
        populateTopLevelNestedValues(left, right)
        left.nestedThing.nestedLongThing = TestUtil.objectFactory().property(Long).value(5 as Long)
        right.nestedThing.nestedLongThing = TestUtil.objectFactory().property(Long).value(6 as Long)
        left.anotherNestedThing.nestedShortThing = TestUtil.objectFactory().property(Short).value(7 as Short)
        right.anotherNestedThing.nestedShortThing = TestUtil.objectFactory().property(Short).value(8 as Short)
    }

    private static InstancePairTypeMetadataWalker.InstancePair<?> instancePair(Class<?> commonType, Object left, Object right) {
        return InstancePairTypeMetadataWalker.InstancePair.of(commonType, left, right)
    }

    private class TestVisitor implements InstancePairTypeMetadataWalker.InstancePairMetadataVisitor {
        private final List<CollectedNode> all = []
        private final List<CollectedNode> roots = []
        private final List<CollectedNode> nested = []
        private final List<CollectedNode> leaves = []
        private final List<Exception> errors = []

        protected void addNode(CollectedNode node) {
            all.add(node)
        }

        @Override
        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, @Nullable InstancePairTypeMetadataWalker.InstancePair<?> pair) {
            CollectedNode node = new CollectedNode(typeMetadata, qualifiedName, pair)
            addNode(node)
            nested.add(node)
        }

        @Override
        void visitNestedUnpackingError(String qualifiedName, Exception e) {
            errors.add(e)
        }

        @Override
        void visitLeaf(InstancePairTypeMetadataWalker.InstancePair<?> parent, String qualifiedName, PropertyMetadata propertyMetadata) {
            CollectedNode node = new CollectedNode(null, qualifiedName, instancePair(propertyMetadata.declaredType.rawType, propertyMetadata.getPropertyValue(parent.getLeft()), propertyMetadata.getPropertyValue(parent.getRight())))
            addNode(node)
            leaves.add(node)
        }

        @Override
        void visitRoot(TypeMetadata typeMetadata, InstancePairTypeMetadataWalker.InstancePair<?> pair) {
            CollectedNode node = new CollectedNode(typeMetadata, null, pair)
            addNode(node)
            roots.add(node)
        }
    }

    @EqualsAndHashCode
    static class CollectedNode {
        TypeMetadata typeMetadata
        String qualifiedName
        InstancePairTypeMetadataWalker.InstancePair<?> pair

        CollectedNode(TypeMetadata typeMetadata, String qualifiedName, InstancePairTypeMetadataWalker.InstancePair<?> pair) {
            this.typeMetadata = typeMetadata
            this.qualifiedName = qualifiedName
            this.pair = pair
        }

        @Override
        String toString() {
            return  qualifiedName
        }
    }

    @ThisIsAThing
    static class TestDataObject {
        @Long
        Property<Long> longThing

        @Short
        Property<Short> shortThing

        @TestNested
        NestedThing nestedThing

        @TestNested
        AnotherNestedThing anotherNestedThing
    }

    static class NestedThing {
        @Long
        Property<Long> nestedLongThing
    }

    static class AnotherNestedThing {
        @Short
        Property<Short> nestedShortThing
    }

    @ThisIsAThing
    static class CycleDataObject {
        @TestNested
        CycleNestedThing1 nestedThing

        @TestNested
        CycleNestedThing2 anotherNestedThing
    }

    static class CycleNestedThing1 {
        @TestNested
        CycleNestedThing2 cycleDataObject
    }

    static class CycleNestedThing2 {
        @TestNested
        CycleNestedThing1 cycleDataObject
    }

    @ThisIsAThing
    static class ErrorDataObject {
        @TestNested
        Object getErrorObject() {
            throw new RuntimeException("Error")
        }
    }
}
