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

package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.util.Matchers
import spock.lang.Specification

class NamespaceIdTest extends Specification {
    def "can set namespace and name" () {
        given:
        NamespaceId id = new NamespaceId("some-namespace", "some-name")

        expect:
        id.getNamespace() == "some-namespace"
        id.getName() == "some-name"
    }

    def "hashCode and equals determine equality" () {
        given:
        NamespaceId id1 = new NamespaceId("some-namespace", "some-name")
        NamespaceId id2 = new NamespaceId("some-namespace", "some-name")

        expect:
        id1 Matchers.strictlyEqual(id2)
    }

    def "hashCode and equals determine inequality" () {
        given:
        NamespaceId id1 = new NamespaceId(namespace1, name1)
        NamespaceId id2 = new NamespaceId(namespace2, name2)

        expect:
        ! id1.equals(id2)
        id1.hashCode() != id2.hashCode()

        where:
        namespace1        | name1        | namespace2        | name2
        "some-namespace1" | "some-name"  | "some-namespace2" | "some-name"
        "some-namespace"  | "some-name1" | "some-namespace"  | "some-name2"
        "some-namespace1" | "some-name"  | "some-namespace"  | "some-name2"
    }

    def "toString returns name" () {
        given:
        NamespaceId id = new NamespaceId("some-namespace", "some-name")

        expect:
        id.toString().equals("some-name")
    }

    def "can decode an encoding" () {
        given:
        NamespaceId id = new NamespaceId("some-namespace", "some-name")

        expect:
        NamespaceId.decode(id.encode()) == id
    }
}
