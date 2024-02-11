/*
 * Copyright 2023 the original author or authors.
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


import org.gradle.api.artifacts.component.ComponentIdentifier
import spock.lang.Specification

/**
 * Tests {@link TransformedComponentFileArtifactIdentifier}
 */
class TransformedComponentFileArtifactIdentifierTest extends Specification {
    def "has useful display name"() {
        def componentId = newComponentId("foo")
        def id = new TransformedComponentFileArtifactIdentifier(componentId, "current", "original")

        expect:
        id.getOriginalFileName() == "original"
        id.getFileName() == "current"
        id.getDisplayName() == "original -> current (foo)"
    }

    def "equals and hash code differentiate between same and different instances"() {
        def componentId = newComponentId("foo")

        when:
        def id = new TransformedComponentFileArtifactIdentifier(componentId, "a", "b")
        def same = new TransformedComponentFileArtifactIdentifier(componentId, "a", "b")

        def different1 = new TransformedComponentFileArtifactIdentifier(componentId, "a", "c")
        def different2 = new TransformedComponentFileArtifactIdentifier(componentId, "c", "b")
        def different3 = new TransformedComponentFileArtifactIdentifier(newComponentId("bar"), "a", "b")

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

    ComponentIdentifier newComponentId(String id) {
        Mock(ComponentIdentifier) {
            getDisplayName() >> id
        }
    }
}
