/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Namer
import org.gradle.util.TestUtil

class DefaultNamedDomainObjectListTest extends AbstractNamedDomainObjectCollectionSpec<CharSequence> {
    final Namer<Object> toStringNamer = new Namer<Object>() {
        String determineName(Object object) {
            return object.toString()
        }
    }
    final DefaultNamedDomainObjectList<CharSequence> list = new DefaultNamedDomainObjectList<CharSequence>(CharSequence, TestUtil.instantiatorFactory().decorateLenient(), toStringNamer, callbackActionDecorator)

    DefaultNamedDomainObjectList<String> container = list
    StringBuffer a = new StringBuffer("a")
    StringBuffer b = new StringBuffer("b")
    StringBuffer c = new StringBuffer("c")
    StringBuilder d = new StringBuilder("d")
    boolean externalProviderAllowed = true
    boolean directElementAdditionAllowed = true
    boolean elementRemovalAllowed = true
    boolean supportsBuildOperations = true

    def "can add element at given index"() {
        given:
        list.add('b')

        when:
        list.add(0, 'a')
        list.add(2, 'c')

        then:
        list == ['a', 'b', 'c']
    }

    def "fires events when element is added at index"() {
        Action<String> action = Mock()

        given:
        list.all(action)

        when:
        list.add(0, 'a')
        list.add(0, 'b')

        then:
        1 * action.execute('a')
        1 * action.execute('b')
        0 * action._
    }

    def "cannot add duplicate element by adding element at given index"() {
        given:
        list.add('a')

        when:
        list.add(1, 'a')

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot add a CharSequence with name 'a' as a CharSequence with that name already exists."
        list == ['a']
    }

    def "can add collection at given index"() {
        when:
        list.addAll(0, ['a', 'b'])

        then:
        list == ['a', 'b']

        when:
        list.addAll(1, ['c', 'd'])

        then:
        list == ['a', 'c', 'd', 'b']
    }

    def "fires events when elements are added"() {
        Action<String> action = Mock()

        given:
        list.all(action)

        when:
        list.addAll(0, ['a', 'b'])

        then:
        1 * action.execute('a')
        1 * action.execute('b')
        0 * action._
    }

    def "ignores duplicate elements when adding collection at given index"() {
        given:
        list.add('a')

        when:
        list.addAll(1, ['b', 'a', 'c', 'b'])

        then:
        list == ['a', 'b', 'c']
    }

    def "can get element at given index"() {
        given:
        list.add("a")
        list.add("b")
        list.add("c")

        expect:
        list.get(0) == "a"
        list.get(1) == "b"
    }

    def "can set element at given index"() {
        given:
        list.addAll(['a', 'b', 'c'])

        when:
        def result = list.set(1, 'd')

        then:
        result == 'b'
        list == ['a', 'd', 'c']
    }

    def "cannot add duplicate element by setting element at given index"() {
        given:
        list.addAll(['a', 'b'])

        when:
        list.set(1, 'a')

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot add a CharSequence with name 'a' as a CharSequence with that name already exists."
        list == ['a', 'b']
    }

    def "fires events when element is replaced"() {
        Action<String> addAction = Mock()
        Action<String> removeAction = Mock()

        given:
        list.add('a')
        list.all(addAction)
        list.whenObjectRemoved(removeAction)

        when:
        list.set(0, 'b')

        then:
        1 * removeAction.execute('a')
        1 * addAction.execute('b')
        0 * removeAction._
        0 * addAction._
    }

    def "can remove element at given index"() {
        given:
        list.addAll(['a', 'b', 'c'])

        when:
        def result = list.remove(1)

        then:
        result == 'b'
        list == ['a', 'c']
    }

    def "fires events when element is removed from given index"() {
        Action<String> action = Mock()

        given:
        list.add('a')
        list.whenObjectRemoved(action)

        when:
        list.remove(0)

        then:
        1 * action.execute('a')
        0 * action._
    }

    def "can find index of domain object"() {
        given:
        list.addAll(['a', 'b', 'a'])

        expect:
        list.indexOf('a') == 0
        list.indexOf('other') == -1
    }

    def "can find last index of domain object"() {
        given:
        list.addAll(['a', 'b', 'a'])

        expect:
        list.lastIndexOf('a') == 0  // Duplicates are omitted
        list.lastIndexOf('other') == -1
    }

    def "can iterate over elements using ListIterator"() {
        given:
        list.addAll(['a', 'b', 'c'])

        expect:
        def iter = list.listIterator()
        iter.hasNext()
        iter.next() == 'a'
        iter.hasNext()
        iter.next() == 'b'
        iter.hasNext()
        iter.next() == 'c'
        !iter.hasNext()
    }

    def "can remove element using ListIterator"() {
        given:
        list.addAll(['a', 'b', 'c'])
        def iterator = list.listIterator()

        when:
        iterator.next()
        iterator.remove()

        then:
        iterator.next() == 'b'
        list == ['b', 'c']
    }

    def "fires event when element removed using ListIterator"() {
        given:
        Action<String> action = Mock()
        list.addAll(['a', 'b', 'c'])
        list.whenObjectRemoved(action)

        when:
        def iterator = list.listIterator()
        iterator.next()
        iterator.remove()

        then:
        1 * action.execute('a')
        0 * action._
    }

    def "can set element using ListIterator"() {
        given:
        list.addAll(['a', 'b', 'c'])
        def iterator = list.listIterator()

        when:
        iterator.next()
        iterator.set('d')

        then:
        iterator.next() == 'b'
        list == ['d', 'b', 'c']
    }

    def "fires events when element replaced using ListIterator"() {
        given:
        Action<String> addAction = Mock()
        Action<String> removeAction = Mock()
        list.addAll(['a', 'b', 'c'])
        list.whenObjectAdded(addAction)
        list.whenObjectRemoved(removeAction)

        when:
        def iterator = list.listIterator()
        iterator.next()
        iterator.set('d')

        then:
        1 * removeAction.execute('a')
        1 * addAction.execute('d')
        0 * removeAction._
        0 * addAction._
    }

    def "cannot add duplicate element by replacing using ListIterator"() {
        given:
        list.addAll(['a', 'b'])

        when:
        def iterator = list.listIterator()
        iterator.next()
        iterator.set('b')

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot add a CharSequence with name 'b' as a CharSequence with that name already exists."
        list == ['a', 'b']
    }

    def "can add element using ListIterator"() {
        given:
        list.addAll(['a', 'b', 'c'])
        def iterator = list.listIterator()

        when:
        iterator.next()
        iterator.add('d')

        then:
        iterator.next() == 'b'
        list == ['a', 'd', 'b', 'c']
    }

    def "fires event when element added using ListIterator"() {
        given:
        Action<String> action = Mock()
        list.addAll(['a', 'b', 'c'])
        list.whenObjectAdded(action)

        when:
        def iterator = list.listIterator()
        iterator.next()
        iterator.add('d')

        then:
        1 * action.execute('d')
        0 * action._
    }

    def "cannot add duplicate element by adding using ListIterator"() {
        given:
        list.addAll(['a', 'b'])

        when:
        def iterator = list.listIterator()
        iterator.next()
        iterator.add('b')

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot add a CharSequence with name 'b' as a CharSequence with that name already exists."
        list == ['a', 'b']
    }

    def "can iterate over elements from given position using ListIterator"() {
        given:
        list.addAll(['a', 'b', 'c'])

        expect:
        def iter = list.listIterator(1)
        iter.hasNext()
        iter.hasPrevious()
        iter.next() == 'b'
        iter.hasNext()
        iter.next() == 'c'
        !iter.hasNext()
    }

    def "can get sublist of elements"() {
        given:
        list.addAll(['a', 'b', 'c'])

        expect:
        list.subList(1, 3) == ['b', 'c']
    }

    def "cannot mutate sublist"() {
        when:
        list.subList(0, 0).clear()

        then:
        thrown(UnsupportedOperationException)
    }

    def "can find all elements that match closure"() {
        given:
        list.addAll(["a", "b", "c"])

        expect:
        list.findAll { it != "b" } == ["a", "c"]
    }

    def "name based filtering does not realize pending"() {
        given:
        list.add("realized1")
        list.addLater(new TestNamedProvider("unrealized1", "unrealized1"))
        list.add("realized2")
        list.addLater(new TestNamedProvider("unrealized2", "unrealized2"))

        expect: "unrealized elements remain as such"
        list.index.asMap().size() == 2
        list.index.pendingAsMap.size() == 2

        when: "filter the list via the `named` method"
        def filtered = list.named { it.contains("2") }

        then: "unrealized elements remain as such"
        list.index.asMap().size() == 2
        list.index.pendingAsMap.size() == 2

        filtered.index.asMap().size() == 1
        filtered.index.pendingAsMap.size() == 1
    }

    def "can get filtered element by index"() {
        given:
        list.addAll(["a", "b", "c"])

        expect:
        list.matching { it != "b" }.get(0) == "a"
        list.matching { it != "b" }.get(1) == "c"

        when:
        list.matching { it != "b" }.get(43)

        then:
        IndexOutOfBoundsException e = thrown()
    }

    def "can get index of filtered element"() {
        given:
        list.addAll(["a", "b", "c"])

        expect:
        list.matching { it != "b" }.indexOf("a") == 0
        list.matching { it != "b" }.indexOf("c") == 1
        list.matching { it != "b" }.indexOf("b") == -1
        list.matching { it != "b" }.indexOf("z") == -1
    }

    def "can get last index of filtered element"() {
        given:
        list.addAll(["a", "b", "c"])

        expect:
        list.matching { it != "b" }.lastIndexOf("a") == 0
        list.matching { it != "b" }.lastIndexOf("c") == 1
        list.matching { it != "b" }.lastIndexOf("b") == -1
        list.matching { it != "b" }.lastIndexOf("z") == -1
    }

    def "can get ListIterator over filtered elements"() {
        given:
        list.addAll(["a", "b", "c"])

        when:
        def iter = list.matching { it != "b" }.listIterator()

        then:
        iter.hasNext()
        iter.nextIndex() == 0
        iter.next() == "a"
        iter.nextIndex() == 1
        iter.next() == "c"
        !iter.hasNext()
        iter.nextIndex() == 2
    }

    def "can get ListIterator over filtered elements starting at given index"() {
        given:
        list.addAll(["a", "b", "c"])

        when:
        def iter = list.matching { it != "b" }.listIterator(1)

        then:
        iter.hasNext()
        iter.nextIndex() == 1
        iter.next() == "c"
        !iter.hasNext()
    }

    @Override
    protected Map<String, Closure> getMutatingMethods() {
        return super.getMutatingMethods() + [
            "add(int, T)": { container.add(0, b) },
            "addAll(int, Collection)": { container.addAll(0, [b]) },
            "set(int, T)": { container.set(0, b) },
            "remove(int)": { container.remove(0) },
            "listIterator().add(T)": { def iter = container.listIterator(); iter.next(); iter.add(b) },
            "listIterator().set(T)": { def iter = container.listIterator(); iter.next(); iter.set(b) },
            "listIterator().remove()": { def iter = container.listIterator(); iter.next(); iter.remove() },
        ]
    }

}
