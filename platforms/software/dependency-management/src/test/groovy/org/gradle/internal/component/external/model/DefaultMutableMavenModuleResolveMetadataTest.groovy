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
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ImmutableModuleSources
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.component.model.MutableModuleSources
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil

class DefaultMutableMavenModuleResolveMetadataTest extends AbstractMutableModuleComponentResolveMetadataTest {
    private final mavenMetadataFactory = new MavenMutableModuleMetadataFactory(new DefaultImmutableModuleIdentifierFactory(), AttributeTestUtil.attributesFactory(), TestUtil.objectInstantiator(), DependencyManagementTestUtil.defaultSchema())

    @Override
    AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List<DependencyMetadata> dependencies) {
        mavenMetadataFactory.create(id, dependencies) as AbstractMutableModuleComponentResolveMetadata
    }

    @Override
    AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id) {
        mavenMetadataFactory.create(id, []) as AbstractMutableModuleComponentResolveMetadata
    }

    def "defines configurations for maven scopes and several usage buckets"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def metadata = mavenMetadataFactory.create(id, [])

        expect:
        def immutable = metadata.asImmutable()
        immutable.configurationNames == ["compile", "runtime", "test", "provided", "system", "optional", "master", "default", "javadoc", "sources"] as Set
        immutable.getConfiguration("compile").hierarchy as List == ["compile"]
        immutable.getConfiguration("runtime").hierarchy as List == ["runtime", "compile"]
        immutable.getConfiguration("master").hierarchy as List == ["master"]
        immutable.getConfiguration("test").hierarchy as List == ["test", "runtime", "compile"]
        immutable.getConfiguration("default").hierarchy as List == ["default", "runtime", "compile", "master"]
        immutable.getConfiguration("provided").hierarchy as List == ["provided"]
        immutable.getConfiguration("optional").hierarchy as List == ["optional"]
    }

    def "default metadata"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def metadata = mavenMetadataFactory.create(id, [])

        expect:
        metadata.packaging == 'jar'
        !metadata.relocated
        metadata.snapshotTimestamp == null

        def immutable = metadata.asImmutable()
        !immutable.missing
        immutable.packaging == 'jar'
        !immutable.relocated
        immutable.configurationNames == ["compile", "runtime", "test", "provided", "system", "optional", "master", "default", "javadoc", "sources"] as Set
        immutable.variants.empty
    }

    def "initialises values from descriptor state and defaults"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")

        def vid = Mock(ModuleVersionIdentifier)
        def metadata = mavenMetadataFactory.create(id, [])

        expect:
        metadata.id == id
        metadata.status == "integration"

        and:
        metadata.sources == new MutableModuleSources()
        metadata.statusScheme == ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        metadata.snapshotTimestamp == null
        metadata.packaging == "jar"
        !metadata.relocated

        and:
        def immutable = metadata.asImmutable()
        immutable != metadata
        immutable.id == id
        immutable.sources == ImmutableModuleSources.of()
        immutable.statusScheme == ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        immutable.snapshotTimestamp == null
        immutable.packaging == "jar"
        !immutable.relocated
        immutable.getConfiguration("compile").artifacts.size() == 1
        immutable.getConfiguration("runtime").artifacts.size() == 1
        immutable.getConfiguration("default").artifacts.size() == 1
        immutable.getConfiguration("master").artifacts.size() == 1
        !immutable.getConfiguration("unknown")

        and:
        def copy = immutable.asMutable()
        copy != metadata
        copy.id == id
        copy.sources == new MutableModuleSources()
        copy.statusScheme == ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        copy.snapshotTimestamp == null
        copy.packaging == "jar"
        !copy.relocated

        and:
        def immutable2 = copy.asImmutable()
        immutable2.getConfiguration("compile").artifacts.size() == 1
        immutable2.getConfiguration("runtime").artifacts.size() == 1
        immutable2.getConfiguration("default").artifacts.size() == 1
        immutable2.getConfiguration("master").artifacts.size() == 1
        !immutable.getConfiguration("unknown")

        when: 'metadata has non-standard packaging'
        copy.packaging = 'hk2-jar'

        then:
        def immutable3 = copy.asImmutable()
        immutable3.packaging == 'hk2-jar'
        immutable3.getConfiguration("compile").artifacts.size() == 1
        immutable3.getConfiguration("runtime").artifacts.size() == 1
        immutable3.getConfiguration("default").artifacts.size() == 1
        immutable3.getConfiguration("master").artifacts.size() == 1
        !immutable.getConfiguration("unknown")
    }

    def "can override values from descriptor"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def newId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.2")
        def sources = ImmutableModuleSources.of(Stub(ModuleSource))

        def metadata = mavenMetadataFactory.create(id, [])

        when:
        metadata.id = newId
        metadata.sources = sources
        metadata.status = "3"
        metadata.changing = true
        metadata.statusScheme = ["1", "2", "3"]
        metadata.snapshotTimestamp = "123"
        metadata.packaging = "pom"
        metadata.relocated = true

        then:
        metadata.id == newId
        metadata.moduleVersionId == DefaultModuleVersionIdentifier.newId(newId)
        metadata.sources == MutableModuleSources.of(sources)
        metadata.changing
        metadata.status == "3"
        metadata.statusScheme == ["1", "2", "3"]
        metadata.snapshotTimestamp == "123"
        metadata.packaging == "pom"
        metadata.relocated

        def immutable = metadata.asImmutable()
        immutable != metadata
        immutable.id == newId
        immutable.moduleVersionId == DefaultModuleVersionIdentifier.newId(newId)
        immutable.sources == sources
        immutable.status == "3"
        immutable.changing
        immutable.statusScheme == ["1", "2", "3"]
        immutable.snapshotTimestamp == "123"
        immutable.packaging == "pom"
        immutable.relocated

        def copy = immutable.asMutable()
        copy != metadata
        copy.id == newId
        copy.moduleVersionId == DefaultModuleVersionIdentifier.newId(newId)
        copy.sources == MutableModuleSources.of(sources)
        copy.status == "3"
        copy.changing
        copy.statusScheme == ["1", "2", "3"]
        copy.snapshotTimestamp == "123"
        copy.packaging == "pom"
        copy.relocated
    }

    def "making changes to copy does not affect original"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def newId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.2")
        def sources = ImmutableModuleSources.of(Stub(ModuleSource))
        def metadata = mavenMetadataFactory.create(id, [])

        when:
        def immutable = metadata.asImmutable()
        def copy = immutable.asMutable()
        copy.id = newId
        copy.sources = MutableModuleSources.of(sources)
        copy.changing = true
        copy.status = "3"
        copy.statusScheme = ["2", "3"]
        copy.snapshotTimestamp = "123"
        copy.packaging = "pom"
        copy.relocated = true
        def immutableCopy = copy.asImmutable()

        then:
        metadata.id == id
        metadata.sources == new MutableModuleSources()
        metadata.statusScheme == ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        metadata.snapshotTimestamp == null
        metadata.packaging == "jar"
        !metadata.relocated

        immutable.id == id
        immutable.sources == ImmutableModuleSources.of()
        immutable.statusScheme == ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        immutable.snapshotTimestamp == null
        immutable.packaging == "jar"
        !immutable.relocated

        copy.id == newId
        copy.sources == MutableModuleSources.of(sources)
        copy.statusScheme == ["2", "3"]
        copy.snapshotTimestamp == "123"
        copy.packaging == "pom"
        copy.relocated

        immutableCopy.id == newId
        immutableCopy.sources == sources
        immutableCopy.statusScheme == ["2", "3"]
        immutableCopy.snapshotTimestamp == "123"
        immutableCopy.packaging == "pom"
        immutableCopy.relocated
    }

    def "making changes to original does not affect copy"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def newId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.2")
        def sources = ImmutableModuleSources.of(Stub(ModuleSource))
        def metadata = mavenMetadataFactory.create(id, [])

        when:
        def immutable = metadata.asImmutable()

        metadata.id = newId
        metadata.sources = MutableModuleSources.of(sources)
        metadata.statusScheme = ["1", "2"]
        metadata.snapshotTimestamp = "123"
        metadata.packaging = "pom"
        metadata.relocated = true

        def immutableCopy = metadata.asImmutable()

        then:
        metadata.id == newId
        metadata.sources == MutableModuleSources.of(sources)
        metadata.statusScheme == ["1", "2"]
        metadata.snapshotTimestamp == "123"
        metadata.packaging == "pom"
        metadata.relocated

        immutable.id == id
        immutable.sources == ImmutableModuleSources.of()
        immutable.statusScheme == ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        immutable.snapshotTimestamp == null
        immutable.packaging == "jar"
        !immutable.relocated

        immutableCopy.id == newId
        immutableCopy.sources == sources
        immutableCopy.statusScheme == ["1", "2"]
        immutableCopy.snapshotTimestamp == "123"
        immutableCopy.packaging == "pom"
        immutableCopy.relocated
    }
}
