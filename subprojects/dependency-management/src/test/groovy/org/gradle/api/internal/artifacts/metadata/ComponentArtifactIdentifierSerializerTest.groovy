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
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.serialize.SerializerSpec

class ComponentArtifactIdentifierSerializerTest extends SerializerSpec {
    ComponentArtifactIdentifierSerializer serializer = new ComponentArtifactIdentifierSerializer()

    def "converts ModuleComponentArtifactMetadata"() {
        given:
        ModuleComponentArtifactIdentifier identifier = new DefaultModuleComponentArtifactIdentifier(DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version"), "art-name", "type", "ext", "classifier")

        when:
        ModuleComponentArtifactIdentifier result = serialize(identifier, serializer)

        then:
        result.componentIdentifier.group == 'group'
        result.componentIdentifier.module == 'module'
        result.componentIdentifier.version == 'version'
        result.name.name == "art-name"
        result.name.type == "type"
        result.name.extension == "ext"
        result.name.classifier == "classifier"
    }
}
