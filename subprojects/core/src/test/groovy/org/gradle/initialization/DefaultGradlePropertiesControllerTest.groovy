/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.initialization

import org.gradle.api.internal.properties.GradleProperties
import spock.lang.Specification

class DefaultGradlePropertiesControllerTest extends Specification {

    def "attached GradleProperties #method fails before loading"() {

        given:
        def propertiesLoader = Mock(IGradlePropertiesLoader)
        def subject = new DefaultGradlePropertiesController(propertiesLoader)
        def properties = subject.gradleProperties
        0 * propertiesLoader.loadGradleProperties(_)

        when:
        switch (method) {
            case "find": properties.find("anything"); break
            case "mergeProperties": properties.mergeProperties([:]); break
            default: assert false
        }

        then:
        thrown(IllegalStateException)

        where:
        method << ["find", "mergeProperties"]
    }

    def "attached GradleProperties methods succeed after loading"() {

        given:
        def settingsDir = new File('.')
        def propertiesLoader = Mock(IGradlePropertiesLoader)
        def subject = new DefaultGradlePropertiesController(propertiesLoader)
        def properties = subject.gradleProperties
        def loadedProperties = Mock(GradleProperties)
        1 * propertiesLoader.loadGradleProperties(settingsDir) >> loadedProperties
        1 * loadedProperties.mergeProperties(_) >> [property: '42']
        1 * loadedProperties.find(_) >> '42'

        when:
        subject.loadGradlePropertiesFrom(settingsDir)

        then:
        properties.find("property") == '42'
        properties.mergeProperties([:]) == [property: '42']
    }

    def "loadGradlePropertiesFrom is idempotent"() {

        given:
        // use a different File instance for each call to ensure it is compared by value
        def currentDir = { new File('.') }
        def propertiesLoader = Mock(IGradlePropertiesLoader)
        def subject = new DefaultGradlePropertiesController(propertiesLoader)
        def loadedProperties = Mock(GradleProperties)

        when: "calling the method multiple times with the same value"
        subject.loadGradlePropertiesFrom(currentDir())
        subject.loadGradlePropertiesFrom(currentDir())

        then:
        1 * propertiesLoader.loadGradleProperties(currentDir()) >> loadedProperties
    }

    def "loadGradlePropertiesFrom fails when called with different argument"() {

        given:
        def settingsDir = new File('a')
        def propertiesLoader = Mock(IGradlePropertiesLoader)
        def subject = new DefaultGradlePropertiesController(propertiesLoader)
        def loadedProperties = Mock(GradleProperties)
        1 * propertiesLoader.loadGradleProperties(settingsDir) >> loadedProperties

        when:
        subject.loadGradlePropertiesFrom(settingsDir)
        subject.loadGradlePropertiesFrom(new File('b'))

        then:
        thrown(IllegalStateException)
    }
}
