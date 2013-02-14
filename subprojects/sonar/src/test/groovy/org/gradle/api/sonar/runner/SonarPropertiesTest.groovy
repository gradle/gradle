/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.sonar.runner

import spock.lang.Specification

class SonarPropertiesTest extends Specification {
    def properties = new SonarProperties()

    def "set a single property"() {
        when:
        properties.property "foo", "one"

        then:
        properties.properties == [foo: "one"]
    }

    def "set multiple properties at once"() {
        when:
        properties.properties foo: "one", bar: "two"

        then:
        properties.properties == [foo:  "one", bar: "two"]
    }

    def "read and write the properties map directly"() {
        when:
        properties.properties = [foo: "one", bar: "two"]
        properties.properties.bar *= 2
        properties.properties.remove("foo")

        then:
        properties.properties == [bar: "twotwo"]
    }
}
