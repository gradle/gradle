/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.provider

import org.gradle.api.internal.provider.support.LazyGroovySupport
import org.gradle.api.internal.provider.support.SupportsCompoundAssignment
import org.gradle.api.provider.Provider
import org.gradle.internal.evaluation.CircularEvaluationException
import spock.lang.Specification

abstract class CompoundAssignmentPropertySpec<T extends LazyGroovySupport & Provider<?>> extends Specification {
    // This test checks various stages of the rewritten compound assignment expression.
    // In short, the compound assignment to a field: lhs += ["a"] is rewritten into:
    //      def intermediate = wrap(lhs) + ["a"]
    //      lhs = intermediate -> setLhs(intermediate) -> lhs.setFromAnyValue(intermediate)
    //      unwrap(intermediate) <- this becomes the value of the expression (not the field)
    // The compound assignment to a variable is simpler:
    //      def intermediate = wrap(lhs) + ["a"]
    //      lhs = intermediate
    //      unwrap(intermediate) <- this is the value of the expression (not the variable)
    // See SupportsCompoundAssignment javadoc for details.
    protected PropertyHost host = Mock()

    abstract T property()

    abstract def value(String v)

    abstract def asValue(def v)

    abstract def addValue(T property, String v)

    def "compound operand can be applied to property"() {
        given:
        def lhs = property()

        when:
        def intermediate = SupportsCompoundAssignment.wrap(lhs) + value("a")
        lhs.setFromAnyValue(intermediate)
        SupportsCompoundAssignment.unwrap(intermediate)

        then:
        asValue(lhs.get()) == value("a")
    }

    def "compound operand can be applied to variable"() {
        given:
        def lhs = property()

        when:
        def intermediate = SupportsCompoundAssignment.wrap(lhs) + value("a")
        lhs = intermediate
        SupportsCompoundAssignment.unwrap(intermediate)

        then:
        asValue(lhs.get()) == value("a")
    }

    def "variable updated with compound operand tracks lhs updates"() {
        given:
        def origin = property()
        def lhs = origin
        def intermediate = SupportsCompoundAssignment.wrap(lhs) + value("a")
        lhs = intermediate
        SupportsCompoundAssignment.unwrap(intermediate)

        when:
        addValue(origin, "b")

        then:
        // note that lhs += "a" results in lhs + ["a"], so adding "b" to lhs effectively prepends it to the result.
        asValue(lhs.get()) == value("b") + value("a")
    }

    def "special compound handling only applies inside the compound assignment expression"() {
        // It is possible to snatch the result of the compound assignment and assign it back to the LHS property instance.
        // For example, here origin is a field. In plain Groovy code it looks like:
        // def lhs = owner.origin
        // lhs += ["a"] // lhs now is the sum provider.
        // owner.origin = lhs // there is no special handling when assigning this provider even though it originates from compound assignment involving this property.
        given:
        final def origin = property()
        def lhs = origin
        def intermediate = SupportsCompoundAssignment.wrap(lhs) + value("a")
        lhs = intermediate
        SupportsCompoundAssignment.unwrap(intermediate)

        when:
        origin.setFromAnyValue(lhs)
        origin.get()

        then:
        thrown(CircularEvaluationException)
    }

    def "compound assignment expression with variable has null value"() {
        given:
        def lhs = property()

        when:
        def intermediate = SupportsCompoundAssignment.wrap(lhs) + value("a")

        then:
        SupportsCompoundAssignment.unwrap(intermediate) == null
    }

    def "compound assignment expression with field has null value"() {
        given:
        def lhs = property()

        when:
        def intermediate = SupportsCompoundAssignment.wrap(lhs) + value("a")
        lhs.setFromAnyValue(intermediate)

        then:
        SupportsCompoundAssignment.unwrap(intermediate) == null
    }

    static class CompoundAssignmentListPropertyTest extends CompoundAssignmentPropertySpec<DefaultListProperty<String>> {
        @Override
        DefaultListProperty<String> property() { new DefaultListProperty<>(host, String) }

        @Override
        def value(String v) { [v] }

        @Override
        def asValue(def result) { result as List<String> }

        @Override
        def addValue(DefaultListProperty<String> property, String v) { property.add(v) }
    }

    static class CompoundAssignmentSetPropertyTest extends CompoundAssignmentPropertySpec<DefaultSetProperty<String>> {
        @Override
        DefaultSetProperty<String> property() { new DefaultSetProperty<>(host, String) }

        @Override
        def value(String v) { [v] }

        @Override
        def asValue(def result) { result as List<String> }

        @Override
        def addValue(DefaultSetProperty<String> property, String v) { property.add(v) }
    }

    static class CompoundAssignmentMapPropertyTest extends CompoundAssignmentPropertySpec<DefaultMapProperty<String, String>> {
        @Override
        DefaultMapProperty<String, String> property() { new DefaultMapProperty<>(host, String, String) }

        @Override
        def value(String v) { [(v): v] }

        @Override
        def asValue(def result) { result as Map<String, String> }

        @Override
        def addValue(DefaultMapProperty<String, String> property, String v) { property.put(v, v) }
    }
}
