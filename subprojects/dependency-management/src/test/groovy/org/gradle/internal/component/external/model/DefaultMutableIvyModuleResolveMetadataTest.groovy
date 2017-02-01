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
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.external.descriptor.MutableModuleDescriptorState
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.ModuleSource

class DefaultMutableIvyModuleResolveMetadataTest extends AbstractMutableModuleComponentResolveMetadataTest {
    @Override
    AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, ModuleDescriptorState moduleDescriptor, List<Configuration> configurations, List<DependencyMetadata> dependencies) {
        return new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, moduleDescriptor, configurations, dependencies)
    }

    @Override
    AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, Set<IvyArtifactName> artifacts) {
        return new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, artifacts)
    }

    def "initialises values from descriptor state and defaults"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def descriptor = new MutableModuleDescriptorState(id, "2", true)
        descriptor.branch = "b"
        configuration("runtime", [])
        configuration("default", ["runtime"])

        def vid = Mock(ModuleVersionIdentifier)

        expect:
        def metadata = new DefaultMutableIvyModuleResolveMetadata(vid, id, descriptor, configurations, [])
        metadata.componentId == id
        metadata.id == vid
        metadata.status == "2"

        and:
        metadata.source == null
        !metadata.changing
        metadata.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        metadata.descriptor == descriptor

        and:
        def immutable = metadata.asImmutable()
        immutable != metadata
        immutable.componentId == id
        immutable.source == null
        immutable.id == vid
        immutable.status == "2"
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        immutable.generated
        immutable.branch == "b"
        !immutable.changing
        immutable.configurationNames == ["runtime", "default"] as Set
        immutable.getConfiguration("runtime")
        immutable.getConfiguration("default")
        immutable.getConfiguration("default").hierarchy == ["runtime", "default"] as Set
        immutable.getConfiguration("default").transitive
        immutable.getConfiguration("default").visible

        and:
        def copy = immutable.asMutable()
        copy != metadata
        copy.componentId == id
        copy.source == null
        copy.id == vid
        copy.status == "2"
        copy.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        !copy.changing
    }

    def "can override values from descriptor"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def descriptor = new MutableModuleDescriptorState(id, "2", true)
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "1.2")
        def source = Stub(ModuleSource)

        when:
        def metadata = new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, descriptor, [], [])
        metadata.componentId = newId
        metadata.source = source
        metadata.status = "3"
        metadata.changing = true
        metadata.statusScheme = ["1", "2", "3"]

        then:
        metadata.componentId == newId
        metadata.id == DefaultModuleVersionIdentifier.newId(newId)
        metadata.source == source
        metadata.changing
        metadata.status == "3"
        metadata.statusScheme == ["1", "2", "3"]

        def immutable = metadata.asImmutable()
        immutable != metadata
        immutable.componentId == newId
        immutable.id == DefaultModuleVersionIdentifier.newId(newId)
        immutable.source == source
        immutable.status == "3"
        immutable.changing
        immutable.statusScheme == ["1", "2", "3"]

        def copy = immutable.asMutable()
        copy != metadata
        copy.componentId == newId
        copy.id == DefaultModuleVersionIdentifier.newId(newId)
        copy.source == source
        copy.status == "3"
        copy.changing
        copy.statusScheme == ["1", "2", "3"]
    }

    def "making changes to copy does not affect original"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def descriptor = new MutableModuleDescriptorState(id, "2", true)
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "1.2")
        def source = Stub(ModuleSource)

        when:
        def metadata = new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, descriptor, [], [])
        def immutable = metadata.asImmutable()
        def copy = immutable.asMutable()
        copy.componentId = newId
        copy.source = source
        copy.changing = true
        copy.status = "3"
        copy.statusScheme = ["2", "3"]
        def immutableCopy = copy.asImmutable()

        then:
        metadata.componentId == id
        metadata.source == null
        !metadata.changing
        metadata.status == "2"
        metadata.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME

        immutable.componentId == id
        immutable.source == null
        !immutable.changing
        immutable.status == "2"
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME

        copy.componentId == newId
        copy.source == source
        copy.changing
        copy.status == "3"
        copy.statusScheme == ["2", "3"]

        immutableCopy.componentId == newId
        immutableCopy.source == source
        immutableCopy.changing
        immutableCopy.status == "3"
        immutableCopy.statusScheme == ["2", "3"]
    }

    def "making changes to original does not affect copy"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def descriptor = new MutableModuleDescriptorState(id, "2", true)
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "1.2")
        def source = Stub(ModuleSource)

        when:
        def metadata = new DefaultMutableIvyModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, descriptor, [], [])
        def immutable = metadata.asImmutable()

        metadata.componentId = newId
        metadata.source = source
        metadata.changing = true
        metadata.status = "3"
        metadata.statusScheme = ["1", "2"]

        def immutableCopy = metadata.asImmutable()

        then:
        metadata.componentId == newId
        metadata.source == source
        metadata.changing
        metadata.status == "3"
        metadata.statusScheme == ["1", "2"]

        immutable.componentId == id
        immutable.source == null
        !immutable.changing
        immutable.status == "2"
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME

        immutableCopy.componentId == newId
        immutableCopy.source == source
        immutableCopy.changing
        immutableCopy.status == "3"
        immutableCopy.statusScheme == ["1", "2"]
    }
}
