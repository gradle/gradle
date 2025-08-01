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

package org.gradle.initialization.properties

import org.gradle.api.internal.properties.GradleProperties
import spock.lang.Specification

import java.util.function.Predicate

class FilteringGradlePropertiesTest extends Specification {

    def delegate = Mock(GradleProperties)

    def "find returns null when property name is filtered out"() {
        given:
        def predicate = { String name -> name.startsWith("allowed") } as Predicate<String>
        def filteringProperties = new FilteringGradleProperties(delegate, predicate)

        when:
        def result = filteringProperties.find("blocked.property")

        then:
        result == null
        0 * delegate.find(_)
    }

    def "find delegates to underlying implementation when property name passes filter"() {
        given:
        def predicate = { String name -> name.startsWith("allowed") } as Predicate<String>
        def filteringProperties = new FilteringGradleProperties(delegate, predicate)
        delegate.find("allowed.property") >> "value"

        when:
        def result = filteringProperties.find("allowed.property")

        then:
        result == "value"
    }

    def "find returns null when property name passes filter but delegate returns null"() {
        given:
        def predicate = { String name -> name.startsWith("allowed") } as Predicate<String>
        def filteringProperties = new FilteringGradleProperties(delegate, predicate)
        delegate.find("allowed.property") >> null

        when:
        def result = filteringProperties.find("allowed.property")

        then:
        result == null
    }

    def "getProperties filters out properties that don't match predicate"() {
        given:
        def predicate = { String name -> name.startsWith("keep") } as Predicate<String>
        def filteringProperties = new FilteringGradleProperties(delegate, predicate)
        delegate.getProperties() >> [
            "keep.prop1": "value1",
            "keep.prop2": "value2",
            "remove.prop1": "value3",
            "remove.prop2": "value4"
        ]

        when:
        def result = filteringProperties.getProperties()

        then:
        result == [
            "keep.prop1": "value1",
            "keep.prop2": "value2"
        ]
    }

    def "getProperties returns empty map when no properties match filter"() {
        given:
        def predicate = { String name -> name.startsWith("nonexistent") } as Predicate<String>
        def filteringProperties = new FilteringGradleProperties(delegate, predicate)
        delegate.getProperties() >> [
            "prop1": "value1",
            "prop2": "value2"
        ]

        when:
        def result = filteringProperties.getProperties()

        then:
        result.isEmpty()
    }

    def "getProperties returns all properties when filter accepts everything"() {
        given:
        def predicate = { String name -> true } as Predicate<String>
        def filteringProperties = new FilteringGradleProperties(delegate, predicate)
        def allProps = [
            "prop1": "value1",
            "prop2": "value2",
            "prop3": "value3"
        ]
        delegate.getProperties() >> allProps

        when:
        def result = filteringProperties.getProperties()

        then:
        result == allProps
    }

    def "getPropertiesWithPrefix applies both prefix and filter constraints"() {
        given:
        def predicate = { String name -> !name.contains("exclude") } as Predicate<String>
        def filteringProperties = new FilteringGradleProperties(delegate, predicate)
        delegate.getProperties() >> [
            "org.gradle.prop1": "value1",
            "org.gradle.exclude.prop2": "value2",
            "org.gradle.prop3": "value3",
            "other.gradle.prop4": "value4",
            "org.exclude.prop5": "value5"
        ]

        when:
        def result = filteringProperties.getPropertiesWithPrefix("org.gradle")

        then:
        result == [
            "org.gradle.prop1": "value1",
            "org.gradle.prop3": "value3"
        ]
    }

    def "getPropertiesWithPrefix returns empty map when no properties match both prefix and filter"() {
        given:
        def predicate = { String name -> name.startsWith("allowed") } as Predicate<String>
        def filteringProperties = new FilteringGradleProperties(delegate, predicate)
        delegate.getProperties() >> [
            "org.gradle.prop1": "value1",
            "org.gradle.prop2": "value2"
        ]

        when:
        def result = filteringProperties.getPropertiesWithPrefix("org.gradle")

        then:
        result.isEmpty()
    }

    def "getPropertiesWithPrefix works with empty prefix"() {
        given:
        def predicate = { String name -> name.startsWith("keep") } as Predicate<String>
        def filteringProperties = new FilteringGradleProperties(delegate, predicate)
        delegate.getProperties() >> [
            "keep.prop1": "value1",
            "remove.prop2": "value2",
            "keep.prop3": "value3"
        ]

        when:
        def result = filteringProperties.getPropertiesWithPrefix("")

        then:
        result == [
            "keep.prop1": "value1",
            "keep.prop3": "value3"
        ]
    }
}
