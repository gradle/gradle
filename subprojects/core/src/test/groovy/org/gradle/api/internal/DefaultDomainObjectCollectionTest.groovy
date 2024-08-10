/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal

import groovy.test.NotYetImplemented
import org.gradle.api.Action
import org.gradle.api.internal.collections.IterationOrderRetainingSetElementSource
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.specs.Spec
import org.gradle.internal.code.UserCodeSource
import org.gradle.util.TestUtil

import static org.gradle.util.internal.WrapUtil.toList

class DefaultDomainObjectCollectionTest extends AbstractDomainObjectCollectionSpec<CharSequence> {
    DefaultDomainObjectCollection<CharSequence> container = new DefaultDomainObjectCollection<CharSequence>(CharSequence.class, new IterationOrderRetainingSetElementSource<CharSequence>(), callbackActionDecorator)
    StringBuffer a = new StringBuffer("a")
    StringBuffer b = new StringBuffer("b")
    StringBuffer c = new StringBuffer("c")
    StringBuilder d = new StringBuilder("d")
    boolean externalProviderAllowed = true
    boolean directElementAdditionAllowed = true
    boolean elementRemovalAllowed = true
    boolean supportsBuildOperations = true

    def canGetAllMatchingDomainObjectsOrderedByOrderAdded() {
        def spec = new Spec<CharSequence>() {
            boolean isSatisfiedBy(CharSequence element) {
                return element != "b"
            }
        }

        container.add("a")
        container.add("b")
        container.add("c")

        expect:
        toList(container.matching(spec)) == ["a", "c"]
    }

    def getAllMatchingDomainObjectsReturnsEmptySetWhenNoMatches() {
        def spec = new Spec<CharSequence>() {
            boolean isSatisfiedBy(CharSequence element) {
                return false
            }
        }

        container.add("a")

        expect:
        container.matching(spec).empty
    }

    def canGetFilteredCollectionContainingAllObjectsWhichMeetSpec() {
        def spec = new Spec<CharSequence>() {
            boolean isSatisfiedBy(CharSequence element) {
                return element != "b"
            }
        }
        def testClosure = {
            return it != "b"
        }

        container.add("a")
        container.add("b")
        container.add("c")

        expect:
        toList(container.matching(spec)) == ["a", "c"]
        toList(container.matching(testClosure)) == ["a", "c"]
    }

    def filteredCollectionIsLive() {
        def spec = new Spec<CharSequence>() {
            boolean isSatisfiedBy(CharSequence element) {
                return element != "a"
            }
        };

        container.add("a")

        expect:
        def filteredCollection = container.matching(spec)
        filteredCollection.isEmpty()

        container.add("b")
        container.add("c")

        toList(filteredCollection) == ["b", "c"]
    }

    def filteredCollectionExecutesActionWhenMatchingObjectAdded() {
        def spec = new Spec<CharSequence>() {
            boolean isSatisfiedBy(CharSequence element) {
                return element != "a"
            }
        }
        def action = Mock(Action)

        given:
        container.matching(spec).whenObjectAdded(action)

        when:
        container.add("a")

        then:
        0 * action._

        when:
        container.add("b")

        then:
        1 * action.execute("b")
        0 * action._
    }

    def filteredCollectionExecutesClosureWhenMatchingObjectAdded() {
        def seen = []
        def closure = { seen << it }

        def spec = new Spec<CharSequence>() {
            boolean isSatisfiedBy(CharSequence element) {
                return element != "a"
            }
        }

        given:
        container.matching(spec).whenObjectAdded(closure)

        when:
        container.add("a")

        then:
        seen.empty

        when:
        container.add("b")

        then:
        seen == ["b"]
    }

    def canChainFilteredCollections() {
        def spec = new Spec<CharSequence>() {
            boolean isSatisfiedBy(CharSequence element) {
                return element != "b"
            }
        }
        def spec2 = new Spec<String>() {
            boolean isSatisfiedBy(String element) {
                return element != "c"
            }
        }

        given:
        container.add("a")
        container.add("b")
        container.add("c")
        container.add(new StringBuffer("d"))

        def collection = container.matching(spec).withType(String.class).matching(spec2);

        expect:
        toList(collection) == ["a"]
    }

    def restoresUserCodeApplicationWhenFilterSpecIsEvaluated() {
        def spec = Mock(Spec)
        def source = Stub(UserCodeSource)
        def collection = null

        given:
        userCodeApplicationContext.apply(source) {
            collection = container.matching(spec)
        }
        assert userCodeApplicationContext.current() == null
        container.add("a")

        when:
        collection.toList()

        then:
        1 * spec.isSatisfiedBy("a") >> {
            assert userCodeApplicationContext.current().source == source
            true
        }
    }

    def findAllRetainsIterationOrder() {
        container.add("a")
        container.add("b")
        container.add("c")

        expect:
        def collection = container.findAll { it != 'b' }
        collection instanceof List
        collection == ["a", "c"]
    }

    def findAllDoesNotReturnALiveCollection() {
        container.add("a")
        container.add("b")
        container.add("c")

        given:
        def collection = container.findAll { it != 'b' }
        container.add("d")

        expect:
        collection == ["a", "c"]
    }

    def callsActionWhenObjectAdded() {
        def action = Mock(Action)

        container.whenObjectAdded(action)

        when:
        container.add("a")

        then:
        1 * action.execute("a")
        0 * action._
    }

    def callsClosureWithNewObjectAsParameterWhenObjectAdded() {
        def seen = []
        def closure = { seen << it }

        container.whenObjectAdded(closure)

        when:
        container.add("a")

        then:
        seen == ["a"]
    }

    def callsClosureWithRemovedObjectAsParameterWhenObjectRemoved() {
        def seen = []
        def closure = { seen << it }

        container.add("a");
        container.whenObjectRemoved(closure)

        when:
        container.remove("a")

        then:
        seen == ["a"]

        when:
        container.remove("a")
        container.remove("b")

        then:
        seen == ["a"]
    }

    def callsClosureWithNewObjectAsDelegateWhenObjectAdded() {
        container.whenObjectAdded { assert delegate == 'a' }

        expect:
        container.add("a")
    }

    def callsRemoveActionWhenObjectRemoved() {
        def action = Mock(Action)

        container.whenObjectRemoved(action)
        container.add("a")

        when:
        container.remove("a")

        then:
        1 * action.execute("a")
        0 * action._

        when:
        container.remove("a")
        container.remove("b")

        then:
        0 * action._
    }

    def callsRemoveActionWhenObjectRemovedUsingIterator() {
        def action = Mock(Action)

        container.whenObjectRemoved(action)
        container.add("a")
        container.add("b")

        def iterator = container.iterator()
        iterator.next()
        iterator.next()

        when:
        iterator.remove()

        then:
        1 * action.execute("b")
        0 * action._

        and:
        toList(container) == ["a"]
    }

    def callsRemoveActionWhenObjectRemovedUsingIteratorNoFlushAndLastElementIsUnrealized() {
        def action = Mock(Action)
        def provider = Mock(ProviderInternal)
        _ * provider.calculateValue(_) >> ValueSupplier.Value.of("c")

        container.whenObjectRemoved(action)
        container.add("a")
        container.add("b")
        container.addLater(provider)

        def iterator = container.iteratorNoFlush()
        while (iterator.hasNext()) {
            iterator.next()
        }

        when:
        iterator.remove()

        then:
        1 * action.execute("b")
        0 * action._

        and:
        toList(container) == ["a", "c"]
    }

    def allCallsActionForEachExistingObject() {
        def action = Mock(Action)

        container.add("a")
        container.add("b")

        when:
        container.all(action)

        then:
        1 * action.execute("a")
        1 * action.execute("b")
        0 * action._
    }

    def allCallsClosureForEachExistingObject() {
        def seen = []
        def closure = { seen << it }

        container.add("a")
        container.add("b")

        when:
        container.all(closure)

        then:
        seen == ["a", "b"]
    }

    def allCallsActionForEachNewObject() {
        def action = Mock(Action)

        container.all(action)

        when:
        container.add("a")

        then:
        1 * action.execute("a")
        0 * action._
    }

    def allCallsClosureForEachNewObject() {
        def seen = []
        def closure = { seen << it }

        container.all(closure)

        when:
        container.add("a")

        then:
        seen == ["a"]
    }

    def allCallsClosureWithObjectAsDelegate() {
        expect:
        container.all { assert delegate == 'a' }
        container.add("a")
    }

    def canRemoveAndMaintainOrder() {
        container.add("b")
        container.add("a")
        container.add("c")

        when:
        container.remove("a")

        then:
        toList(container) == ["b", "c"]
    }

    def canRemoveNonExistentObject() {
        expect:
        !container.remove("a")
    }

    def "calls beforeCollectionChanges before object is added"() {
        def action = Mock(Action)
        container.beforeCollectionChanges(action)

        when:
        container.add("a")

        then:
        1 * action.execute("add(T)")
        0 * _
    }

    def "object is not added when beforeCollectionChanges throws an exception"() {
        def action = Mock(Action)
        def failure = new RuntimeException()
        container.beforeCollectionChanges(action)

        when:
        container.add("a")

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * action.execute("add(T)") >> { throw failure }

        and:
        !toList(container).contains("a")
    }

    def "calls beforeCollectionChanges once before collection is added"() {
        def action = Mock(Action)
        container.beforeCollectionChanges(action)

        when:
        container.addAll(["a", "b"])

        then:
        1 * action.execute("addAll(Collection)")
        0 * _
    }

    def "calls beforeCollectionChanges before object is removed"() {
        def action = Mock(Action)
        container.beforeCollectionChanges(action)

        when:
        container.remove("a")

        then:
        1 * action.execute("remove(Object)")
        0 * _
    }

    def "calls beforeCollectionChanges before object is removed using iterator"() {
        def action = Mock(Action)

        container.add("a")
        container.beforeCollectionChanges(action)

        def iterator = container.iterator()
        iterator.next()

        when:
        iterator.remove()

        then:
        1 * action.execute("iterator().remove()")
        0 * _
    }

    def "object is not removed when beforeCollectionChanges throws an exception"() {
        def action = Mock(Action)
        def failure = new RuntimeException()

        container.add("a")
        container.beforeCollectionChanges(action)

        when:
        container.remove("a")

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * action.execute("remove(Object)") >> { throw failure }

        and:
        toList(container).contains("a")
    }

    def "calls beforeCollectionChanges before collection is cleared"() {
        def action = Mock(Action)
        container.beforeCollectionChanges(action)

        when:
        container.clear()

        then:
        1 * action.execute("clear()")
        0 * _
    }

    def "calls beforeCollectionChanges before collection is removed"() {
        def action = Mock(Action)
        container.beforeCollectionChanges(action)

        when:
        container.removeAll(["a", "b"])

        then:
        1 * action.execute("removeAll(Collection)")
        0 * _
    }

    def "calls beforeCollectionChanges before collection is intersected"() {
        def action = Mock(Action)
        container.add("a")
        container.add("b")
        container.beforeCollectionChanges(action)

        when:
        container.retainAll(toList())

        then:
        1 * action.execute("retainAll(Collection)")
        0 * _
    }

    def "withType works with addLater"() {
        given:
        def value = Mock(Subtype)

        when:
        container.addLater(TestUtil.providerFactory().provider { value })
        def result = collect(container.withType(Subtype))

        then:
        result == [value]
    }

    @NotYetImplemented
    def "withType works with addAllLater"() {
        given:
        def value = Mock(Subtype)

        when:
        container.addAllLater(TestUtil.providerFactory().provider { [value] })
        def result = collect(container.withType(Subtype))

        then:
        result == [value]
    }

    @NotYetImplemented
    def "withType works for addAllLater and list property"() {
        def value = Mock(Subtype)
        def property = TestUtil.objectFactory().listProperty(CharSequence)
        property.add(value)

        when:
        container.addAllLater(property)
        def result = collect(container.withType(Subtype))

        then:
        result == [value]
    }

    interface Subtype extends CharSequence {}

    static <T> Collection<T> collect(Iterable<T> iterable) {
        def result = []
        Iterator it = iterable.iterator()
        while (it.hasNext()) {
            result.add(it.next())
        }
        result
    }
}
