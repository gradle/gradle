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

package org.gradle.internal.logging.text

import spock.lang.Specification

import java.util.function.Consumer

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class TreeFormatterTest extends Specification {
    def formatter = new TreeFormatter()

    def "formats single node"() {
        when:
        formatter.node("Some thing.")

        then:
        formatter.toString() == "Some thing."
    }

    def "formats root with no children"() {
        when:
        formatter.node("Some thing.")
        formatter.startChildren()
        formatter.endChildren()

        then:
        formatter.toString() == "Some thing."
    }

    def "formats root with single leaf child"() {
        when:
        formatter.node("Some things")
        formatter.startChildren()
        formatter.node("child 1")
        formatter.endChildren()

        then:
        formatter.toString() == 'Some things: child 1'
    }

    def "formats root with single nested leaf child"() {
        when:
        formatter.node("Some things")
        formatter.startChildren()
        formatter.node("child 1")
        formatter.startChildren()
        formatter.node("child 2")
        formatter.endChildren()
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators("""Some things:
  - child 1: child 2""")
    }

    def "formats root with single child with multiple children"() {
        when:
        formatter.node("Some things")
        formatter.startChildren()
        formatter.node("child 1")
        formatter.startChildren()
        formatter.node("child 1.1")
        formatter.node("child 1.2")
        formatter.endChildren()
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators("""Some things:
  - child 1:
      - child 1.1
      - child 1.2""")
    }

    def "formats root with multiple leaf children"() {
        when:
        formatter.node("Some things")
        formatter.startChildren()
        formatter.node("child 1")
        formatter.node("child 2")
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators("""Some things:
  - child 1
  - child 2""")
    }

    def "formats nested children"() {
        when:
        formatter.node("Some things")
        formatter.startChildren()
        formatter.node("child 1")
        formatter.startChildren()
        formatter.node("child 1.1")
        formatter.node("child 1.2")
        formatter.endChildren()
        formatter.node("child 2")
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators("""Some things:
  - child 1:
      - child 1.1
      - child 1.2
  - child 2""")
    }

    def "formats node with single child with long text"() {
        def longText = (0..20).join('')

        when:
        formatter.node(longText)
        formatter.startChildren()
        formatter.node(longText)
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators("""${longText}:
  - ${longText}""")
    }

    def "formats node with trailing '.'"() {
        when:
        formatter.node("Some things.")
        formatter.startChildren()
        formatter.node("child 1.")
        formatter.endChildren()
        formatter.node("Some other things.")
        formatter.startChildren()
        formatter.node("child 1.")
        formatter.node("child 2.")
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators("""Some things.
  - child 1.
Some other things.
  - child 1.
  - child 2.""")
    }

    def "formats node with trailing ':'"() {
        when:
        formatter.node("Some things:")
        formatter.startChildren()
        formatter.node("child 1.")
        formatter.endChildren()
        formatter.node("Some other things:")
        formatter.startChildren()
        formatter.node("child 1.")
        formatter.node("child 2.")
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators("""Some things: child 1.
Some other things:
  - child 1.
  - child 2.""")
    }

    def "indents nested children that span multiple lines"() {
        when:
        formatter.node(toPlatformLineSeparators("Multiple\nlines"))
        formatter.startChildren()
        formatter.node("child 1")
        formatter.startChildren()
        formatter.node(toPlatformLineSeparators("multiple\nlines"))
        formatter.node(toPlatformLineSeparators("another\nchild"))
        formatter.endChildren()
        formatter.node(toPlatformLineSeparators("one\ntwo"))
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators("""Multiple
lines:
  - child 1:
      - multiple
        lines
      - another
        child
  - one
    two""")
    }

    def "formats multiple nodes"() {
        when:
        formatter.node("Some thing.")
        formatter.node("Some other thing.")

        then:
        formatter.toString() == toPlatformLineSeparators("""Some thing.
Some other thing.""")
    }

    def "formats multiple nodes with children"() {
        when:
        formatter.node("root1")
        formatter.startChildren()
        formatter.node("child1")
        formatter.node("child2")
        formatter.endChildren()
        formatter.node("root2")
        formatter.startChildren()
        formatter.node("child1")
        formatter.endChildren()
        formatter.node("root3")
        formatter.startChildren()
        formatter.node("child1")
        formatter.node("child2")
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators("""root1:
  - child1
  - child2
root2: child1
root3:
  - child1
  - child2""")
    }

    def "can append to root node"() {
        when:
        formatter.node("Some ")
        formatter.append("thing")
        formatter.append(".")

        then:
        formatter.toString() == "Some thing."
    }

    def "can append to top level node"() {
        when:
        formatter.node("Root")
        formatter.node("Some ")
        formatter.append("thing")
        formatter.append(".")

        then:
        formatter.toString() == toPlatformLineSeparators('''Root
Some thing.''')
    }

    def "can append to child node"() {
        when:
        formatter.node("Root")
        formatter.startChildren()
        formatter.node("some ")
        formatter.append("thing")
        formatter.append(".")
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators('Root: some thing.')
    }

    def "can append to sibling node"() {
        when:
        formatter.node("Root")
        formatter.startChildren()
        formatter.node("child1")
        formatter.node("some ")
        formatter.append("thing")
        formatter.append(".")
        formatter.endChildren()

        then:
        formatter.toString() == toPlatformLineSeparators("""Root:
  - child1
  - some thing.""")
    }

    def "can append class name"() {
        when:
        formatter.node("thing ")
        formatter.appendType(String.class)

        then:
        formatter.toString() == toPlatformLineSeparators("thing String")
    }

    def "can append interface name"() {
        when:
        formatter.node("thing ")
        formatter.appendType(List.class)

        then:
        formatter.toString() == toPlatformLineSeparators("thing List")
    }

    def "can append inner class name"() {
        when:
        formatter.node("thing ")
        formatter.appendType(Thing.Nested.Inner.class)

        then:
        formatter.toString() == toPlatformLineSeparators("thing TreeFormatterTest.Thing.Nested.Inner")
    }

    def "can append parameterized type name"() {
        when:
        formatter.node("thing ")
        formatter.appendType(Thing.genericInterfaces[0])

        then:
        formatter.toString() == toPlatformLineSeparators("thing List<TreeFormatterTest.Thing.Nested>")
    }

    def "can append wildcard type name"() {
        when:
        formatter.node("thing ")
        formatter.appendType(Thing.genericInterfaces[1])

        then:
        formatter.toString() == toPlatformLineSeparators("thing Map<TreeFormatterTest.Thing.Nested, ? extends java.util.function.Consumer<? super T>>")
    }

    def "can append methods"() {
        when:
        formatter.node("thing ")
        formatter.appendMethod(String.getMethod("length"))
        formatter.append(" ")
        formatter.appendMethod(String.getMethod("charAt", int.class))
        formatter.append(" ")
        formatter.appendMethod(String.getMethod("getBytes", String.class))

        then:
        formatter.toString() == toPlatformLineSeparators("thing String.length() String.charAt(int) String.getBytes(String)")
    }

    def "can append annoation name"() {
        when:
        formatter.node("thing ")
        formatter.appendAnnotation(Override.class)

        then:
        formatter.toString() == toPlatformLineSeparators("thing @Override")
    }

    def "can append value"() {
        when:
        formatter.node("thing ")
        formatter.appendValue(12)

        then:
        formatter.toString() == toPlatformLineSeparators("thing 12")
    }

    def "can append string value"() {
        when:
        formatter.node("thing ")
        formatter.appendValue("value")

        then:
        formatter.toString() == toPlatformLineSeparators("thing 'value'")
    }

    def "can append array value"() {
        when:
        formatter.node("thing ")
        formatter.appendValue([12, "value", null] as Object[])

        then:
        formatter.toString() == toPlatformLineSeparators("thing [12, 'value', null]")
    }

    def "can append null value"() {
        when:
        formatter.node("thing ")
        formatter.appendValue(null)

        then:
        formatter.toString() == toPlatformLineSeparators("thing null")
    }

    def "can append array of values"() {
        when:
        formatter.node("thing ")
        formatter.appendValues(["a", 12, null] as Object[])

        then:
        formatter.toString() == toPlatformLineSeparators("thing ['a', 12, null]")
    }

    def "can append typed array of values"() {
        when:
        formatter.node("thing ")
        formatter.appendValues([1, 2, 3] as Number[])

        then:
        formatter.toString() == toPlatformLineSeparators("thing [1, 2, 3]")
    }

    def "can append empty array of values"() {
        when:
        formatter.node("thing ")
        formatter.appendValues([] as Object[])

        then:
        formatter.toString() == toPlatformLineSeparators("thing []")
    }

    def "can append array of types"() {
        when:
        formatter.node("thing ")
        formatter.appendTypes(Class, Number, String)

        then:
        formatter.toString() == toPlatformLineSeparators("thing (Class, Number, String)")
    }

    def "can append empty array of types"() {
        when:
        formatter.node("thing ")
        formatter.appendTypes()

        then:
        formatter.toString() == toPlatformLineSeparators("thing ()")
    }

    def "cannot append array of types with null"() {
        when:
        formatter.node("thing ")
        formatter.appendTypes(Class, Number, null)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'type cannot be null'
    }

    def "cannot append after children started"() {
        when:
        formatter.node("Root")
        formatter.startChildren()
        formatter.append("thing")

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot append text as there is no current node.'
    }

    def "cannot append after children finished"() {
        when:
        formatter.node("Root")
        formatter.startChildren()
        formatter.node("child")
        formatter.endChildren()
        formatter.append("thing")

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot append text as there is no current node.'
    }

    interface Thing<T> extends List<Nested>, Map<Nested, ? extends Consumer<? super T>> {
        interface Nested {
            class Inner {}
        }
    }
}
