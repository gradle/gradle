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
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import org.gradle.internal.serialize.SerializerSpec
import org.gradle.util.Path

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
        ModuleComponentIdentifier identifier = new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId('group-one', 'name-one'), 'version-one')

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

    def "serializes root ProjectComponentIdentifier"() {
        given:
        def identifier = new DefaultProjectComponentIdentifier(new DefaultBuildIdentifier(Path.path(":build")), Path.ROOT, Path.ROOT, "someProject")

        when:
        def result = serialize(identifier, serializer) as ProjectComponentIdentifierInternal

        then:
        result.identityPath == identifier.identityPath
        result.projectPath == identifier.projectPath
        result.buildTreePath == identifier.buildTreePath
        result.projectName == identifier.projectName
        assertSameProjectId(result, identifier)
    }

    def "serializes root build ProjectComponentIdentifier"() {
        given:
        def identifier = new DefaultProjectComponentIdentifier(new DefaultBuildIdentifier(Path.path(":build")), Path.path(":a:b"), Path.path(":a:b"), "b")

        when:
        def result = serialize(identifier, serializer) as ProjectComponentIdentifierInternal

        then:
        result.identityPath == identifier.identityPath
        result.projectPath == identifier.projectPath
        result.buildTreePath == identifier.buildTreePath
        result.projectName == identifier.projectName
        assertSameProjectId(result, identifier)
    }

    def "serializes other build root ProjectComponentIdentifier"() {
        given:
        def identifier = new DefaultProjectComponentIdentifier(new DefaultBuildIdentifier(Path.path(":build")), Path.path(":prefix:someProject"), Path.ROOT, "someProject")

        when:
        def result = serialize(identifier, serializer) as ProjectComponentIdentifierInternal

        then:
        result.identityPath == identifier.identityPath
        result.projectPath == identifier.projectPath
        result.buildTreePath == identifier.buildTreePath
        result.projectName == identifier.projectName
        assertSameProjectId(result, identifier)
    }

    def "serializes other build ProjectComponentIdentifier"() {
        given:
        def identifier = new DefaultProjectComponentIdentifier(new DefaultBuildIdentifier(Path.path(":build")), Path.path(":prefix:a:b"), Path.path(":a:b"), "b")

        when:
        def result = serialize(identifier, serializer) as ProjectComponentIdentifierInternal

        then:
        result.identityPath == identifier.identityPath
        result.projectPath == identifier.projectPath
        result.buildTreePath == identifier.buildTreePath
        result.projectName == identifier.projectName
        assertSameProjectId(result, identifier)
    }

    def "serialize OpaqueComponentArtifactIdentifier"() {
        given:
        def file = new File("example-1.0.jar")
        def identifier = new OpaqueComponentArtifactIdentifier(file)

        when:
        def result = serialize(identifier, serializer)

        then:
        result.displayName == file.name
        result.file == file
        result.componentIdentifier == identifier
        result == identifier
    }

    def "serialize OpaqueComponentIdentifier"() {
        given:
        def notation = DependencyFactoryInternal.ClassPathNotation.GRADLE_API
        def identifier = new OpaqueComponentIdentifier(notation)

        when:
        def result = serialize(identifier, serializer)

        then:
        result.displayName == notation.displayName
        result.classPathNotation == notation
        result == identifier
    }

    void assertSameProjectId(ProjectComponentIdentifierInternal result, ProjectComponentIdentifierInternal selector) {
        assert result.projectIdentity.buildIdentifier == selector.projectIdentity.buildIdentifier
        assert result.projectIdentity.buildTreePath == selector.projectIdentity.buildTreePath
        assert result.projectIdentity.projectPath == selector.projectIdentity.projectPath
        assert result.projectIdentity.projectName == selector.projectIdentity.projectName
    }
}
