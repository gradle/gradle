/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.classpath

import spock.lang.Specification

import java.util.function.BiConsumer

class AccessTrackingPropertiesTest extends Specification {
    def "access to existing property with get() is tracked"() {
        given:
        Properties props = propertiesWithContent someProperty: 'someValue'
        BiConsumer<String, Object> consumer = Mock()
        AccessTrackingProperties trackingProperties = new AccessTrackingProperties(props, consumer)

        when:
        def returnedValue = trackingProperties.get('someProperty')

        then:
        returnedValue == 'someValue'
        1 * consumer.accept('someProperty', 'someValue')
    }

    def "access to existing property with getOrDefault() is tracked"() {
        given:
        Properties props = propertiesWithContent someProperty: 'someValue'
        BiConsumer<String, Object> consumer = Mock()
        AccessTrackingProperties trackingProperties = new AccessTrackingProperties(props, consumer)

        when:
        def returnedValue = trackingProperties.getOrDefault('someProperty', 'defaultValue')

        then:
        returnedValue == 'someValue'
        1 * consumer.accept('someProperty', 'someValue')
    }

    def "access to existing property with getProperty() is tracked"() {
        given:
        Properties props = propertiesWithContent someProperty: 'someValue'
        BiConsumer<String, Object> consumer = Mock()
        AccessTrackingProperties trackingProperties = new AccessTrackingProperties(props, consumer)

        when:
        def returnedValue = trackingProperties.get('someProperty')

        then:
        returnedValue == 'someValue'
        1 * consumer.accept('someProperty', 'someValue')
    }

    def "access to existing property with getProperty() with default is tracked"() {
        given:
        Properties props = propertiesWithContent someProperty: 'someValue'
        BiConsumer<String, Object> consumer = Mock()
        AccessTrackingProperties trackingProperties = new AccessTrackingProperties(props, consumer)

        when:
        def returnedValue = trackingProperties.get('someProperty', 'defaultValue')

        then:
        returnedValue == 'someValue'
        1 * consumer.accept('someProperty', 'someValue')
    }

    def "access to existing property with entrySet() is tracked"() {
        given:
        Properties props = propertiesWithContent someProperty: 'someValue'
        BiConsumer<String, Object> consumer = Mock()
        AccessTrackingProperties trackingProperties = new AccessTrackingProperties(props, consumer)

        when:
        def returnedEntry = trackingProperties.entrySet().iterator().next()

        then:
        returnedEntry.key == 'someProperty' && returnedEntry.value == 'someValue'
        1 * consumer.accept('someProperty', 'someValue')
    }

    def "access to existing property with forEach() is tracked"() {
        given:
        Properties props = propertiesWithContent someProperty: 'someValue'
        BiConsumer<String, Object> consumer = Mock()
        AccessTrackingProperties trackingProperties = new AccessTrackingProperties(props, consumer)

        when:
        trackingProperties.forEach { k, v -> }

        then:
        1 * consumer.accept('someProperty', 'someValue')
    }

    def "access to missing property with get() is tracked"() {
        given:
        Properties props = propertiesWithContent someProperty: 'someValue'
        BiConsumer<String, Object> consumer = Mock()
        AccessTrackingProperties trackingProperties = new AccessTrackingProperties(props, consumer)

        when:
        def returnedValue = trackingProperties.get('missingProperty')

        then:
        returnedValue == null
        1 * consumer.accept('missingProperty', null)
    }

    def "access to missing property with getOrDefault() is tracked"() {
        given:
        Properties props = propertiesWithContent someProperty: 'someValue'
        BiConsumer<String, Object> consumer = Mock()
        AccessTrackingProperties trackingProperties = new AccessTrackingProperties(props, consumer)

        when:
        def returnedValue = trackingProperties.getOrDefault('missingProperty', 'defaultValue')

        then:
        returnedValue == 'defaultValue'
        1 * consumer.accept('missingProperty', null)
    }

    def "access to missing property with getProperty() is tracked"() {
        given:
        Properties props = propertiesWithContent someProperty: 'someValue'
        BiConsumer<String, Object> consumer = Mock()
        AccessTrackingProperties trackingProperties = new AccessTrackingProperties(props, consumer)

        when:
        def returnedValue = trackingProperties.getProperty('missingProperty')

        then:
        returnedValue == null
        1 * consumer.accept('missingProperty', null)
    }

    def "access to missing property with getProperty() with default is tracked"() {
        given:
        Properties props = propertiesWithContent someProperty: 'someValue'
        BiConsumer<String, Object> consumer = Mock()
        AccessTrackingProperties trackingProperties = new AccessTrackingProperties(props, consumer)

        when:
        def returnedValue = trackingProperties.getProperty('missingProperty', 'defaultValue')

        then:
        returnedValue == 'defaultValue'
        1 * consumer.accept('missingProperty', null)
    }


    private static Properties propertiesWithContent(Map<String, String> contents) {
        Properties props = new Properties()
        props.putAll(contents)
        return props
    }
}
