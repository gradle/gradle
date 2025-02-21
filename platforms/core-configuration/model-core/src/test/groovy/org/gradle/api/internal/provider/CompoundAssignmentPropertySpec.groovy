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

import groovy.transform.CompileStatic
import org.gradle.api.internal.provider.support.LazyGroovySupport
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.internal.evaluation.CircularEvaluationException
import spock.lang.Specification

@TransformCompoundAssignments
abstract class CompoundAssignmentPropertySpec<P extends Provider<?>, T extends LazyGroovySupport & P> extends Specification {
    protected PropertyHost host = Mock()

    abstract T propertyImpl()

    P property() { propertyImpl() }

    abstract def value(String v)

    abstract def asValue(def v)

    abstract def addValue(P property, String v)

    class Origin {
        protected T value = propertyImpl()

        P getValue() { value }

        void setValue(def v) {
            // This simulates what the AsmBackedClassGenerator does.
            value.setFromAnyValue(v)
        }
    }

    Origin newOrigin() { new Origin() }

    def "compound operand can be applied to property"() {
        given:
        def lhs = new Origin()

        expect:
        (lhs.value += value("a")) == null
        asValue(lhs.value.get()) == value("a")
    }

    def "compound operand can be applied to variable"() {
        given:
        def lhs = property()

        expect:
        (lhs += value("a")) == null
        asValue(lhs.get()) == value("a")
    }

    def "updating the variable with compound operand does not change the property value"() {
        // The in-place update magic only kicks in when LHS is a property of something.
        // The plain assignment follows the same rule.
        given:
        def origin = property()
        addValue(origin, "a")
        def lhs = origin

        when:
        lhs += value("b")

        then:
        asValue(lhs.get()) == value("a") + value("b")
        asValue(origin.get()) == value("a")
    }

    def "variable updated with compound operand tracks lhs updates"() {
        given:
        def origin = new Origin()
        def lhs = origin.value

        when:
        lhs += value("a")
        addValue(origin.value, "b")

        then:
        // note that lhs += "a" results in lhs + ["a"], so adding "b" to lhs effectively prepends it to the result.
        asValue(lhs.get()) == value("b") + value("a")
    }

    def "special compound handling only applies inside the compound assignment expression"() {
        // It is possible to snatch the result of the compound assignment and assign it back to the LHS property instance outside of += expression.
        given:
        def origin = new Origin()
        def lhs = origin.value
        lhs += value("a")
        origin.value = lhs

        when:
        origin.value.get()

        then:
        thrown(CircularEvaluationException)
    }

    def "special compound handling does not apply to sums"() {
        given:
        def origin = new Origin()
        def sum = origin.value + value("a")
        origin.value = sum

        when:
        origin.value.get()

        then:
        thrown(CircularEvaluationException)
    }

    @TransformCompoundAssignments
    static class CompoundAssignmentListPropertyTest extends CompoundAssignmentPropertySpec<ListProperty<String>, DefaultListProperty<String>> {
        class ListOrigin extends Origin {
            @Override
            ListProperty<String> getValue() {
                return super.getValue() as ListProperty<String>
            }
        }

        @Override
        ListOrigin newOrigin() { new ListOrigin() }

        @Override
        DefaultListProperty<String> propertyImpl() { new DefaultListProperty<>(host, String) }

        @Override
        def value(String v) { [v] }

        @Override
        def asValue(def result) { result as List<String> }

        @Override
        def addValue(ListProperty<String> property, String v) { property.add(v) }

        // Static compilation doesn't allow us to have these tests in the parent test suite.
        // For brevity, we're only testing these with ListProperty.
        @CompileStatic
        def staticCompilationWorksWithVariables() {
            def p = property()

            def v = (p += ["a"])
            return [v, p]
        }

        def "compound assignment works with variables and static compilation"() {
            when:
            def (v, p) = staticCompilationWorksWithVariables()

            then:
            v == null
            asValue(p.get()) == ["a"]
        }

        @CompileStatic
        def staticCompilationWorksWithProperties() {
            def origin = newOrigin()

            def v = (origin.value += ["a"])
            return [v, origin.value]
        }

        def "compound assignment works with properties and static compilation"() {
            when:
            def (v, p) = staticCompilationWorksWithProperties()

            then:
            v == null
            asValue(p.get()) == ["a"]
        }
    }

    static class CompoundAssignmentSetPropertyTest extends CompoundAssignmentPropertySpec<SetProperty<String>, DefaultSetProperty<String>> {
        @Override
        DefaultSetProperty<String> propertyImpl() { new DefaultSetProperty<>(host, String) }

        @Override
        def value(String v) { [v] }

        @Override
        def asValue(def result) { result as List<String> }

        @Override
        def addValue(SetProperty<String> property, String v) { property.add(v) }
    }

    static class CompoundAssignmentMapPropertyTest extends CompoundAssignmentPropertySpec<MapProperty<String, String>, DefaultMapProperty<String, String>> {
        @Override
        DefaultMapProperty<String, String> propertyImpl() { new DefaultMapProperty<>(host, String, String) }

        @Override
        def value(String v) { [(v): v] }

        @Override
        def asValue(def result) { result as Map<String, String> }

        @Override
        def addValue(MapProperty<String, String> property, String v) { property.put(v, v) }
    }
}
