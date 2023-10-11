/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.util.Matchers
import spock.lang.Specification

class ComponentFileArtifactIdentifierTest extends Specification {

    static class DummyComponentIdentifier implements ComponentIdentifier {
        @Override
        String getDisplayName() {
            return "example"
        }

        String toString() {
            return getDisplayName()
        }
    }

    def "has useful string representation"() {
        def componentId = new DummyComponentIdentifier()

        expect:
        new ComponentFileArtifactIdentifier(componentId, "thing").toString() == "thing (example)"
    }

    def "identifiers are equal when display names are equal"() {
        def componentId = new DummyComponentIdentifier()
        def otherId = new DummyComponentIdentifier()

        def id = new ComponentFileArtifactIdentifier(componentId, "one")
        def sameId = new ComponentFileArtifactIdentifier(componentId, "one")
        def differentComponent = new ComponentFileArtifactIdentifier(otherId, "one")
        def differentName = new ComponentFileArtifactIdentifier(componentId, "two")

        expect:
        Matchers.strictlyEquals(id, sameId)
        id != differentComponent
        id != differentName
    }
}
