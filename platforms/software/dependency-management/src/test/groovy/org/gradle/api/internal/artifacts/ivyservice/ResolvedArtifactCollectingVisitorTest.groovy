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

package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapability
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import spock.lang.Specification

class ResolvedArtifactCollectingVisitorTest extends Specification {

    def "variants with different capabilities are collected"() {
        given:
        def visitor = new ResolvedArtifactCollectingVisitor()
        def componentIdentifier = Mock(ComponentIdentifier) {
            getDisplayName() >> "example"
        }
        def variantName = Mock(DisplayName) {
            getDisplayName() >> "variant"
            getCapitalizedDisplayName() >> "Variant"
        }
        def artifactIdentifier = new ComponentFileArtifactIdentifier(componentIdentifier, "out")
        def projectCapability = new ImmutableCapability("group", "project", "1.0")
        def testFixturesCapability = new ImmutableCapability("group", "project-test-fixtures", "1.0")
        def artifact = Mock(ResolvableArtifact) {
            getId() >> artifactIdentifier
        }

        when:
        visitor.visitArtifact(variantName, ImmutableAttributes.EMPTY, [projectCapability], artifact)
        visitor.visitArtifact(variantName, ImmutableAttributes.EMPTY, [testFixturesCapability], artifact)

        then:
        visitor.getArtifacts().size() == 1
        visitor.getArtifacts().first().variants.size() == 2
    }
}
