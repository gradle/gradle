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

package org.gradle.buildinit.plugins.internal

import spock.lang.Specification


class NamespaceBuilderTest extends Specification {
    def "converts name to namespace"() {
        expect:
        NamespaceBuilder.toNamespace(value) == namespace

        where:
        value   | namespace
        ""      | ""
        "thing" | "thing"
        "a123"  | "a123"
        "_"     | "_"
        "a.b.c" | "a_b_c"
    }

    def "discards invalid identifier characters"() {
        expect:
        NamespaceBuilder.toNamespace(value) == namespace

        where:
        value      | namespace
        "123"      | ""
        "1thing"   | "thing"
        "1a 2b 3c" | "a_b_c"
    }

    def "maps separator chars"() {
        expect:
        NamespaceBuilder.toNamespace(value) == namespace

        where:
        value      | namespace
        ":"        | ""
        "  "       | ""
        "~"        | ""
        "-"        | ""
        ":a"       | "a"
        ":abc"     | "abc"
        ":a:b:c"   | "a_b_c"
        ":a:b:c:"  | "a_b_c"
        "a-b-c"    | "a_b_c"
        "-a-b-c-"  | "a_b_c"
        "::a::b::" | "a_b"
        "a b c"    | "a_b_c"
        "  a b c " | "a_b_c"
        "~abc"     | "abc"
        "~a~b~c~"  | "a_b_c"
    }
}
