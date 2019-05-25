/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.util

import org.junit.runner.Description
import org.junit.runners.model.Statement
import spock.lang.Specification

class SetSystemPropertiesTest extends Specification {
    public static final String TEST_PROPERTY = 'org.gradle.foo'
    def base = Mock(Statement)

    def "can set system properties for the duration of a test"() {
        def properties = [:]

        given:
        System.setProperty(TEST_PROPERTY, "bar")
        properties[TEST_PROPERTY] = "baz"
        SetSystemProperties setSystemProperties = new SetSystemProperties(properties)

        when:
        setSystemProperties.apply(base, Stub(Description)).evaluate()

        then:
        1 * base.evaluate() >> { assert System.getProperty(TEST_PROPERTY) == "baz" }

        and:
        System.getProperty(TEST_PROPERTY) == "bar"
    }

    def "cannot set java.io.tmpdir"() {
        given:
        SetSystemProperties setSystemProperties = new SetSystemProperties(["java.io.tmpdir": "/some/path"])

        when:
        setSystemProperties.apply(base, Stub(Description)).evaluate()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "'java.io.tmpdir' should not be set via a rule as its value cannot be changed once it is initialized"
    }
}
