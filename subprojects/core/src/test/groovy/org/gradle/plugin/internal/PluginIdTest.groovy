/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.internal

import org.gradle.util.Matchers
import spock.lang.Specification

import static DefaultPluginId.validate

class PluginIdTest extends Specification {

    def "test validation matcher"() {
        expect:
        DefaultPluginId.INVALID_PLUGIN_ID_CHAR_MATCHER.indexIn(input) == index

        where:
        input     | index
        "foo"     | -1
        "f o"     | 1
        "foo.bür" | 5
        "123"     | -1
        "FOO.bar" | -1
    }

    def "validate valid"() {
        when:
        validate("foo")
        validate("Foo")
        validate("foo.bar")
        validate("foo.Bar")
        validate("1")
        validate("1.1")
        validate("_._")
        validate("-.-")
        validate("-")

        then:
        noExceptionThrown()
    }

    def "is qualified"() {
        expect:
        !new DefaultPluginId("foo").qualified
        new DefaultPluginId("foo.bar").qualified
    }

    def "qualify if unqualified"() {
        expect:
        new DefaultPluginId("foo").maybeQualify("bar").toString() == "bar.foo"
        new DefaultPluginId("foo.bar").maybeQualify("bar").toString() == "foo.bar"
    }

    def "equality"() {
        expect:
        new DefaultPluginId("foo") Matchers.strictlyEqual(new DefaultPluginId("foo"))
        new DefaultPluginId("foo.bar") Matchers.strictlyEqual(new DefaultPluginId("foo.bar"))
        def qualified = new DefaultPluginId("foo").maybeQualify("some.org")
        qualified Matchers.strictlyEqual(new DefaultPluginId("some.org.foo"))
        new DefaultPluginId("foo") != new DefaultPluginId("foo.bar")
    }

}
