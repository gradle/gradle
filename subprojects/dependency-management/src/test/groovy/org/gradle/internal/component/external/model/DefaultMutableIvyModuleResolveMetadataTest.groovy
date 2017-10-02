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

package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.hash.HashValue

class DefaultMutableIvyModuleResolveMetadataTest extends AbstractMutableModuleComponentResolveMetadataTest {
    @Override
    AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List<DependencyMetadata> dependencies) {
        return new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, configurations, dependencies, [])
    }

    @Override
    AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id) {
        return new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id)
    }

    List<Artifact> artifacts = []
    List<Exclude> excludes = []

    def "initialises values from descriptor state and defaults"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        configuration("runtime", [])
        configuration("default", ["runtime"])
        artifact("runtime.jar", "runtime")
        artifact("api.jar", "default")

        def vid = Mock(ModuleVersionIdentifier)

        expect:
        def metadata = new DefaultMutableIvyModuleResolveMetadata(vid, id, configurations, [], artifacts)
        metadata.componentId == id
        metadata.id == vid
        metadata.branch == null

        and:
        metadata.contentHash == AbstractMutableModuleComponentResolveMetadata.EMPTY_CONTENT
        metadata.source == null
        metadata.artifactDefinitions.size() == 2
        metadata.excludes.empty

        and:
        def immutable = metadata.asImmutable()
        immutable != metadata
        immutable.componentId == id
        immutable.source == null
        immutable.id == vid
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        immutable.branch == null
        immutable.excludes.empty
        immutable.configurationNames == ["runtime", "default"] as Set
        immutable.getConfiguration("runtime")
        immutable.getConfiguration("runtime").artifacts.size() == 1
        immutable.getConfiguration("default")
        immutable.getConfiguration("default").hierarchy == ["default", "runtime"]
        immutable.getConfiguration("default").transitive
        immutable.getConfiguration("default").visible
        immutable.getConfiguration("default").artifacts.size() == 2

        and:
        def copy = immutable.asMutable()
        copy != metadata
        copy.componentId == id
        copy.source == null
        copy.id == vid
        copy.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        copy.branch == null
        copy.artifactDefinitions.size() == 2
        copy.excludes.empty
    }

    def "can override values from descriptor"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "1.2")
        def source = Stub(ModuleSource)
        def contentHash = new HashValue("123")
        def excludes = [new DefaultExclude(new DefaultModuleIdentifier("group", "name"))]

        when:
        def metadata = new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [], [], [])
        metadata.componentId = newId
        metadata.source = source
        metadata.status = "3"
        metadata.branch = "release"
        metadata.changing = true
        metadata.missing = true
        metadata.statusScheme = ["1", "2", "3"]
        metadata.contentHash = contentHash
        metadata.excludes = excludes

        then:
        metadata.componentId == newId
        metadata.id == DefaultModuleVersionIdentifier.newId(newId)
        metadata.source == source
        metadata.changing
        metadata.missing
        metadata.status == "3"
        metadata.branch == "release"
        metadata.statusScheme == ["1", "2", "3"]
        metadata.contentHash == contentHash
        metadata.excludes == excludes

        def immutable = metadata.asImmutable()
        immutable != metadata
        immutable.componentId == newId
        immutable.id == DefaultModuleVersionIdentifier.newId(newId)
        immutable.source == source
        immutable.status == "3"
        immutable.branch == "release"
        immutable.changing
        immutable.missing
        immutable.statusScheme == ["1", "2", "3"]
        immutable.contentHash == contentHash
        immutable.excludes == excludes

        def copy = immutable.asMutable()
        copy != metadata
        copy.componentId == newId
        copy.id == DefaultModuleVersionIdentifier.newId(newId)
        copy.source == source
        copy.status == "3"
        copy.branch == "release"
        copy.changing
        copy.missing
        copy.statusScheme == ["1", "2", "3"]
        copy.contentHash == contentHash
        copy.excludes == excludes
    }

    def "making changes to copy does not affect original"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "1.2")
        def source = Stub(ModuleSource)

        when:
        def metadata = new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [], [], [])
        def immutable = metadata.asImmutable()
        def copy = immutable.asMutable()
        copy.componentId = newId
        copy.source = source
        copy.statusScheme = ["2", "3"]
        def immutableCopy = copy.asImmutable()

        then:
        metadata.componentId == id
        metadata.source == null
        metadata.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME

        immutable.componentId == id
        immutable.source == null
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME

        copy.componentId == newId
        copy.source == source
        copy.statusScheme == ["2", "3"]

        immutableCopy.componentId == newId
        immutableCopy.source == source
        immutableCopy.statusScheme == ["2", "3"]
    }

    def "making changes to original does not affect copy"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "1.2")
        def source = Stub(ModuleSource)

        when:
        def metadata = new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [], [], [])
        def immutable = metadata.asImmutable()

        metadata.componentId = newId
        metadata.source = source
        metadata.statusScheme = ["1", "2"]

        def immutableCopy = metadata.asImmutable()

        then:
        metadata.componentId == newId
        metadata.source == source
        metadata.statusScheme == ["1", "2"]

        immutable.componentId == id
        immutable.source == null
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME

        immutableCopy.componentId == newId
        immutableCopy.source == source
        immutableCopy.statusScheme == ["1", "2"]
    }

    def "can replace the exclude rules for the component"() {
        when:
        configuration("compile")
        configuration("runtime", ["compile"])

        def metadata = getMetadata()
        metadata.configurations

        def exclude1 = exclude("foo", "bar", "runtime")
        def exclude2 = exclude("foo", "baz", "compile")
        metadata.excludes = [exclude1, exclude2]

        then:
        metadata.excludes == [exclude1, exclude2]

        def immutable = metadata.asImmutable()
        immutable.getConfiguration("compile").excludes.size() == 2 // maintains a reference to all the excludes
        immutable.getConfiguration("runtime").excludes.size() == 2

        when:
        def copy = immutable.asMutable()
        copy.excludes = [exclude1]

        then:
        def immutable2 = copy.asImmutable()
        immutable2.getConfiguration("compile").excludes.size() == 1
        immutable2.getConfiguration("runtime").excludes.size() == 1
    }

    def exclude(String group, String module, String... confs) {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId(group, module), confs, "whatever")
        excludes.add(exclude)
        return exclude
    }

    def artifact(String name, String... confs) {
        artifacts.add(new Artifact(new DefaultIvyArtifactName(name, "type", "ext", "classifier"), confs as Set<String>))
    }
}
