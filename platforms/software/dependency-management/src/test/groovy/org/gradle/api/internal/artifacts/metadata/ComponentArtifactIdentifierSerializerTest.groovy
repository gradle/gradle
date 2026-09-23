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

package org.gradle.api.internal.artifacts.metadata

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.publish.ImmutablePublishArtifact
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.local.model.MissingLocalArtifactMetadata
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.local.model.TransformedArtifactIdentifier
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.serialize.SerializerSpec

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class ComponentArtifactIdentifierSerializerTest extends SerializerSpec {
    static def projectId = newProjectId(":lib")
    static def moduleId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.0")

    def serializer = new ComponentArtifactIdentifierSerializer(new ComponentIdentifierSerializer())

    def "round trips #id"() {
        when:
        def result = serialize(id, serializer)

        then:
        result == id
        result.class == id.class
        result.componentIdentifier == id.componentIdentifier

        where:
        id << [
            new DefaultModuleComponentArtifactIdentifier(moduleId, "module", "jar", "jar"),
            new ModuleComponentFileArtifactIdentifier(moduleId, "module-1.0.jar"),
            new ComponentFileArtifactIdentifier(moduleId, "module-1.0.jar"),
            new ComponentFileArtifactIdentifier(projectId, "lib.jar"),
            new OpaqueComponentArtifactIdentifier(new File("/libs/opaque.jar").absoluteFile),
            projectArtifact("/build/libs/lib.jar"),
            new MissingLocalArtifactMetadata(projectId, new DefaultIvyArtifactName("lib", "jar", "jar")),
            new MissingLocalArtifactMetadata(moduleId, new DefaultIvyArtifactName("module", "jar", null, "sources")),
        ]
    }

    def "round trips transformed artifact identifier with input #input"() {
        given:
        def id = new TransformedArtifactIdentifier(input, "transformed.txt")

        when:
        def result = serialize(id, serializer) as TransformedArtifactIdentifier

        then:
        result == id
        result.inputArtifactId == input
        result.inputArtifactId.class == input.class
        result.componentIdentifier == input.componentIdentifier
        result.fileName == "transformed.txt"

        where:
        input << [
            new DefaultModuleComponentArtifactIdentifier(moduleId, "module", "jar", "jar"),
            new ModuleComponentFileArtifactIdentifier(moduleId, "module-1.0.jar"),
            new ComponentFileArtifactIdentifier(projectId, "lib.jar"),
            new OpaqueComponentArtifactIdentifier(new File("/libs/opaque.jar").absoluteFile),
            projectArtifact("/build/classes/java/main"),
            new MissingLocalArtifactMetadata(projectId, new DefaultIvyArtifactName("lib", "jar", "jar")),
        ]
    }

    def "round trips chained transformed artifact identifier"() {
        given:
        def original = projectArtifact("/build/libs/lib.jar")
        def intermediate = new TransformedArtifactIdentifier(original, "lib.jar.txt")
        def id = new TransformedArtifactIdentifier(intermediate, "out")

        when:
        def result = serialize(id, serializer) as TransformedArtifactIdentifier

        then:
        result == id
        result.inputArtifactId == intermediate
        (result.inputArtifactId as TransformedArtifactIdentifier).inputArtifactId == original
    }

    def "distinct project artifacts with the same file name remain distinct after round trip"() {
        given:
        def input1 = projectArtifact("/build/classes/java/main")
        def input2 = projectArtifact("/build/classes/kotlin/main")
        def id1 = new TransformedArtifactIdentifier(input1, "main.txt")
        def id2 = new TransformedArtifactIdentifier(input2, "main.txt")

        when:
        def result1 = serialize(id1, serializer) as TransformedArtifactIdentifier
        def result2 = serialize(id2, serializer) as TransformedArtifactIdentifier

        then:
        id1 != id2
        result1 != result2
        result1 == id1
        result2 == id2
        result1.inputArtifactId == input1
        result2.inputArtifactId == input2
    }

    def "fails to serialize transformed artifact identifier with unsupported input"() {
        given:
        def input = Stub(ComponentArtifactIdentifier)
        def id = new TransformedArtifactIdentifier(input, "lib.jar.txt")

        when:
        serialize(id, serializer)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains(input.getClass().name)
    }

    private static PublishArtifactLocalArtifactMetadata projectArtifact(String path) {
        def file = new File(path).absoluteFile
        return new PublishArtifactLocalArtifactMetadata(
            projectId,
            new ImmutablePublishArtifact(file.name, "", "jar", null, file)
        )
    }
}
