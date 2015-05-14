/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal

import org.gradle.api.Named
import spock.lang.Specification

class NamersTest extends Specification {

    private Named named(String name) {
        { -> name }
    }

    def "assuming Named instances namer provides name returned form getNamed method"() {
        expect:
        Namers.assumingNamed().determineName(named("foo")) == "foo"
    }

    def "assuming Named instances namer throws a sensible exception when not a Named instance is passed to it"() {
        when:
        Namers.assumingNamed().determineName("foo")

        then:
        ClassCastException e = thrown()
        e.message == "Failed to cast object foo of type java.lang.String to target type org.gradle.api.Named"
    }
}
