/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.metadata

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementValueSnapshotterSerializerRegistry
import org.gradle.api.internal.artifacts.TestComponentDescriptorFactory
import org.gradle.api.internal.artifacts.publish.ImmutablePublishArtifact
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.local.model.TransformedComponentFileArtifactIdentifier
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class TransformedComponentFileArtifactIdentifierSerializerTest extends SerializerSpec {
    def registry = new DependencyManagementValueSnapshotterSerializerRegistry(
        new DefaultImmutableModuleIdentifierFactory(),
        AttributeTestUtil.attributesFactory(),
        TestUtil.objectInstantiator(),
        new TestComponentDescriptorFactory()
    )
    Serializer<TransformedComponentFileArtifactIdentifier> serializer = registry.build(TransformedComponentFileArtifactIdentifier)

    def "round trips transformed project artifact identifier"() {
        given:
        def projectId = newProjectId(":lib")
        def input = new PublishArtifactLocalArtifactMetadata(projectId, new ImmutablePublishArtifact("main", "", "java-classes-directory", null, new File("/build/classes/java/main").absoluteFile))
        def id = new TransformedComponentFileArtifactIdentifier(input, "main.txt", "main")

        when:
        def result = serialize(id, serializer)

        then:
        result == id
        result.componentIdentifier == projectId
        result.fileName == "main.txt"
        result.originalFileName == "main"
    }

    def "round trips transformed module artifact identifier"() {
        given:
        def moduleId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.0")
        def input = new DefaultModuleComponentArtifactIdentifier(moduleId, "module", "jar", "jar")
        def id = new TransformedComponentFileArtifactIdentifier(input, "module-1.0.jar.txt", "module-1.0.jar")

        when:
        def result = serialize(id, serializer)

        then:
        result == id
        result.componentIdentifier == moduleId
    }

    def "round trips chained transformed artifact identifier"() {
        given:
        def projectId = newProjectId(":lib")
        def original = new PublishArtifactLocalArtifactMetadata(projectId, new ImmutablePublishArtifact("lib", "jar", "jar", null, new File("/build/libs/lib.jar").absoluteFile))
        def intermediate = new TransformedComponentFileArtifactIdentifier(original, "lib.jar.txt", "lib.jar")
        def id = new TransformedComponentFileArtifactIdentifier(intermediate, "out", "lib.jar")

        when:
        def result = serialize(id, serializer)

        then:
        result == id
        result.inputArtifactId == intermediate
    }

    def "distinct project artifacts with the same file name remain distinct after round trip"() {
        given:
        def projectId = newProjectId(":lib")
        def input1 = new PublishArtifactLocalArtifactMetadata(projectId, new ImmutablePublishArtifact("main", "", "java-classes-directory", null, new File("/build/classes/java/main").absoluteFile))
        def input2 = new PublishArtifactLocalArtifactMetadata(projectId, new ImmutablePublishArtifact("main", "", "java-classes-directory", null, new File("/build/classes/kotlin/main").absoluteFile))
        def id1 = new TransformedComponentFileArtifactIdentifier(input1, "main.txt", "main")
        def id2 = new TransformedComponentFileArtifactIdentifier(input2, "main.txt", "main")

        when:
        ComponentArtifactIdentifier result1 = serialize(id1, serializer)
        ComponentArtifactIdentifier result2 = serialize(id2, serializer)

        then:
        id1 != id2
        result1 != result2
        result1 == id1
        result2 == id2
    }
}
