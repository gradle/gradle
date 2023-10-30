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

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.serialize.SerializerSpec

class ComponentArtifactMetadataSerializerTest extends SerializerSpec {
    ComponentArtifactMetadataSerializer serializer = new ComponentArtifactMetadataSerializer()

    def "converts ModuleComponentArtifactMetadata"() {
        given:
        ModuleComponentArtifactMetadata identifier = new DefaultModuleComponentArtifactMetadata(DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version"), new DefaultIvyArtifactName("art-name", "type", "ext", "classifier"))

        when:
        ModuleComponentArtifactMetadata result = serialize(identifier, serializer)

        then:
        result.id.componentIdentifier.group == 'group'
        result.id.componentIdentifier.module == 'module'
        result.id.componentIdentifier.version == 'version'
        result.name.name == "art-name"
        result.name.type == "type"
        result.name.extension == "ext"
        result.name.classifier == "classifier"
    }
}
