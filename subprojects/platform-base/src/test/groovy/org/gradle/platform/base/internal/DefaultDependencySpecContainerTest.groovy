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

package org.gradle.platform.base.internal

import org.gradle.platform.base.ModuleDependencySpec
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DefaultDependencySpecContainerTest extends Specification {

    @Subject container = new DefaultDependencySpecContainer()

    def "can build module dependency spec starting from either `group` or `module`"() {
        when:
        container.module("m1").group("g1")
        container.group("g2").module("m2")

        then:
        container.dependencies*.displayName == ["g1:m1:*", "g2:m2:*"]
    }

    def "dedupes module dependency specs"() {
        when:
        container.module("m1").group("g1")
        container.group("g1").module("m1")

        then:
        container.dependencies*.displayName == ["g1:m1:*"]
    }

    @Unroll
    def "can build module dependency spec given a module id shorthand notation (#id)"() {
        given:
        ModuleDependencySpec spec = container.module(id).build()

        expect:
        spec.group == group
        spec.name == name
        spec.version == version

        where:
        id          | group | name | version
        "g1:m1:1.0" | "g1"  | "m1" | "1.0"
        "g1:m1"     | "g1"  | "m1" | null    // version is optional
    }
}
