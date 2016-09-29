/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier
import org.gradle.internal.serialize.SerializerSpec

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class ComponentIdentifierSerializerTest extends SerializerSpec {
    ComponentIdentifierSerializer serializer = new ComponentIdentifierSerializer()

    def "throws exception if null is provided"() {
        when:
        serialize(null, serializer)

        then:
        Throwable t = thrown(IllegalArgumentException)
        t.message == 'Provided component identifier may not be null'
    }

    def "serializes ModuleComponentIdentifier"() {
        given:
        ModuleComponentIdentifier identifier = new DefaultModuleComponentIdentifier('group-one', 'name-one', 'version-one')

        when:
        ModuleComponentIdentifier result = serialize(identifier, serializer)

        then:
        result.group == 'group-one'
        result.module == 'name-one'
        result.version == 'version-one'
    }

    def "serializes LibraryIdentifier"() {
        given:
        LibraryBinaryIdentifier identifier = new DefaultLibraryBinaryIdentifier(':project', 'lib', 'variant')

        when:
        LibraryBinaryIdentifier result = serialize(identifier, serializer)

        then:
        result.projectPath == ':project'
        result.libraryName == 'lib'
    }

    def "serializes ProjectComponentIdentifier"() {
        given:
        ProjectComponentIdentifier identifier = newProjectId(':myPath')

        when:
        ProjectComponentIdentifier result = serialize(identifier, serializer)

        then:
        result.projectPath == ':myPath'
    }
}
