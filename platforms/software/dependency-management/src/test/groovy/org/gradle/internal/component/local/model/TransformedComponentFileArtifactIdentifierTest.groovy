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

package org.gradle.internal.component.local.model


import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import spock.lang.Specification

/**
 * Tests {@link TransformedComponentFileArtifactIdentifier}
 */
class TransformedComponentFileArtifactIdentifierTest extends Specification {
    def "has useful display name"() {
        def componentId = newComponentId("foo")
        def id = new TransformedComponentFileArtifactIdentifier(new ComponentFileArtifactIdentifier(componentId, "original"), "current")

        expect:
        id.getFileName() == "current"
        id.getComponentIdentifier() == componentId
        id.getDisplayName() == "original (foo) -> current"
    }

    def "equals and hash code differentiate between same and different instances"() {
        def componentId = newComponentId("foo")
        def inputId = new ComponentFileArtifactIdentifier(componentId, "b")

        when:
        def id = new TransformedComponentFileArtifactIdentifier(inputId, "a")
        def same = new TransformedComponentFileArtifactIdentifier(new ComponentFileArtifactIdentifier(componentId, "b"), "a")

        def different1 = new TransformedComponentFileArtifactIdentifier(inputId, "c")
        def different2 = new TransformedComponentFileArtifactIdentifier(new ComponentFileArtifactIdentifier(componentId, "c"), "a")
        def different3 = new TransformedComponentFileArtifactIdentifier(new ComponentFileArtifactIdentifier(newComponentId("bar"), "b"), "a")

        then:
        id == same
        id.hashCode() == same.hashCode()
        id != different1
        id.hashCode() != different1.hashCode()
        id != different2
        id.hashCode() != different2.hashCode()
        id != different3
        id.hashCode() != different3.hashCode()
    }

    def "distinguishes transformed artifacts whose input artifacts share a file name"() {
        def componentId = newComponentId("foo")
        def input1 = Stub(ComponentArtifactIdentifier) {
            getComponentIdentifier() >> componentId
            getDisplayName() >> "main (foo)"
        }
        def input2 = Stub(ComponentArtifactIdentifier) {
            getComponentIdentifier() >> componentId
            getDisplayName() >> "main (foo)"
        }

        when:
        def id1 = new TransformedComponentFileArtifactIdentifier(input1, "main.txt")
        def id2 = new TransformedComponentFileArtifactIdentifier(input2, "main.txt")

        then:
        id1 != id2
        id1.getDisplayName() == id2.getDisplayName()
    }

    def "distinguishes chained transform outputs with same name from different intermediate artifacts"() {
        def componentId = newComponentId("foo")
        def original = new ComponentFileArtifactIdentifier(componentId, "lib.jar")
        def intermediate1 = new TransformedComponentFileArtifactIdentifier(original, "a.txt")
        def intermediate2 = new TransformedComponentFileArtifactIdentifier(original, "b.txt")

        when:
        def id1 = new TransformedComponentFileArtifactIdentifier(intermediate1, "out")
        def id2 = new TransformedComponentFileArtifactIdentifier(intermediate2, "out")

        then:
        id1 != id2
        id1.getDisplayName() == "lib.jar (foo) -> a.txt -> out"
        id2.getDisplayName() == "lib.jar (foo) -> b.txt -> out"
    }

    ComponentIdentifier newComponentId(String id) {
        Mock(ComponentIdentifier) {
            getDisplayName() >> id
        }
    }
}
