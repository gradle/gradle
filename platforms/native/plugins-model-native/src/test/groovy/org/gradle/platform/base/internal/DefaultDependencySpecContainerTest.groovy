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
import org.gradle.api.IllegalDependencyNotation
import spock.lang.Specification
import spock.lang.Subject

class DefaultDependencySpecContainerTest extends Specification {

    @Subject container = new DefaultDependencySpecContainer()

    def "can build project dependency spec with project or library or both"() {
        when:
        container.project(proj).library(lib)

        then:
        container.dependencies*.displayName == [displayName]

        where:
        proj | lib  | displayName
        "p1" | "l1" | "project 'p1' library 'l1'"
        "p1" | null | "project 'p1'"
        null | "l1" | "library 'l1'"
    }

    def "throws IllegalDependencyNotation for project dependency spec with no project or library"() {
        when:
        container.project(null).library(null)
        container.dependencies

        then:
        def t = thrown(IllegalDependencyNotation)
        t.message == "A project dependency must have at least a project or library name specified."

        where:
        proj | lib  | displayName
        "p1" | "l1" | "project 'p1' library 'l1'"
        "p1" | null | "project 'p1'"
        null | "l1" | "library 'l1'"
    }

    def "throws IllegalDependencyNotation for project dependency spec with invalid library name"() {
        when:
        container.project(null).library("org:my-module:1.0")
        container.dependencies

        then:
        def t = thrown(IllegalDependencyNotation)
        t.message == "'org:my-module:1.0' is not a valid library name. Did you mean to refer to a module instead?"
    }

    def "throws IllegalDependencyNotation when setting project notation value twice"() {
        when:
        container.project(":foo").project(":bar")

        then:
        def t = thrown IllegalDependencyNotation
        t.message == "Cannot set 'project' multiple times for project dependency."

        when:
        container.library("bar").library("bar")

        then:
        t = thrown IllegalDependencyNotation
        t.message == "Cannot set 'library' multiple times for project dependency."
    }

    def "can build module dependency spec starting from either `group` or `module`"() {
        when:
        container.module("m1").group("g1")
        container.group("g2").module("m2")

        then:
        container.dependencies*.displayName == ["g1:m1:+", "g2:m2:+"]
    }

    def "filters duplicate module dependency specs"() {
        when:
        container.module("m1").group("g1")
        container.group("g1").module("m1")

        then:
        container.dependencies*.displayName == ["g1:m1:+"]
    }

    def "filters duplicate project dependency specs"() {
        when:
        container.library('someLib')
        container.project('otherProject').library('someLib')
        container.project('otherProject')

        container.library('someLib')
        container.library('someLib').project('otherProject')
        container.project('otherProject')

        then:
        container.dependencies.size() == 3
        container.dependencies*.displayName == ["library 'someLib'", "project 'otherProject' library 'someLib'", "project 'otherProject'"]
    }

    def "can build module dependency spec given a module id shorthand notation (#id)"() {
        given:
        container.module(id)
        def spec = container.dependencies.first()

        expect:
        spec.group == group
        spec.name == name
        spec.version == version

        where:
        id          | group | name | version
        "g1:m1:1.0" | "g1"  | "m1" | "1.0"
        "g1:m1"     | "g1"  | "m1" | null    // version is optional
    }

    def "throws IllegalDependencyNotation when given shorthand notation containing #description"() {
        when:
        container.module(notation)

        then:
        IllegalDependencyNotation error = thrown()
        error.message.contains("'$notation' is not a valid module dependency notation. Example notations: 'org.gradle:gradle-core:2.2', 'org.mockito:mockito-core'.")

        where:
        description           | notation
        'missing group'       | ':foo'
        'missing module name' | 'foo:'
        'too many components' | 'foo:bar:baz:gazonk'
    }

    def "throws IllegalDependencyNotation for incomplete module notation (#group:#name:#version)"() {
        when:
        container.group(group).module(name).version(version)
        container.dependencies.first()

        then:
        def t = thrown IllegalDependencyNotation
        t.message == "A module dependency must have at least a group and a module name specified."

        where:
        group | name | version
        "g1"  | null | "1.0"
        null  | "m1" | "1.0"
        null  | null | "1.0"
    }

    def "throws IllegalDependencyNotation when setting module notation value twice"() {
        when:
        container.module("org:foo:1.0").module("bar")

        then:
        def t = thrown IllegalDependencyNotation
        t.message == "Cannot set 'module' multiple times for module dependency."

        when:
        container.module("bar").module("org:foo:1.0")

        then:
        t = thrown IllegalDependencyNotation
        t.message == "Cannot set 'module' multiple times for module dependency."

        when:
        container.module("org:foo:1.0").group("bar")

        then:
        t = thrown IllegalDependencyNotation
        t.message == "Cannot set 'group' multiple times for module dependency."

        when:
        container.module("org:foo:1.0").version("3.3")

        then:
        t = thrown IllegalDependencyNotation
        t.message == "Cannot set 'version' multiple times for module dependency."
    }
}
