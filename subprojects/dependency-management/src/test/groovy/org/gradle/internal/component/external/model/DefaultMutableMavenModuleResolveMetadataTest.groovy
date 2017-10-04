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
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.hash.HashValue

class DefaultMutableMavenModuleResolveMetadataTest extends AbstractMutableModuleComponentResolveMetadataTest {
    @Override
    AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List<DependencyMetadata> dependencies) {
        return new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, dependencies)
    }

    @Override
    AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id) {
        return new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id)
    }

    def "defines configurations for maven scopes and several usage buckets"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [])

        expect:
        def immutable = metadata.asImmutable()
        immutable.configurationNames == ["compile", "runtime", "test", "provided", "system", "optional", "master", "default", "javadoc", "sources"] as Set
        immutable.getConfiguration("compile").hierarchy == ["compile"]
        immutable.getConfiguration("runtime").hierarchy == ["runtime", "compile"]
        immutable.getConfiguration("master").hierarchy == ["master"]
        immutable.getConfiguration("test").hierarchy == ["test", "runtime", "compile"]
        immutable.getConfiguration("default").hierarchy == ["default", "runtime", "compile", "master"]
        immutable.getConfiguration("provided").hierarchy == ["provided"]
        immutable.getConfiguration("optional").hierarchy == ["optional"]
    }

    def "default metadata"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id)

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
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")

        def vid = Mock(ModuleVersionIdentifier)
        def metadata = new DefaultMutableMavenModuleResolveMetadata(vid, id, [])

        expect:
        metadata.componentId == id
        metadata.id == vid
        metadata.status == "integration"

        and:
        metadata.source == null
        metadata.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        metadata.snapshotTimestamp == null
        metadata.packaging == "jar"
        !metadata.relocated

        and:
        def immutable = metadata.asImmutable()
        immutable != metadata
        immutable.componentId == id
        immutable.source == null
        immutable.id == vid
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        immutable.snapshotTimestamp == null
        immutable.packaging == "jar"
        !immutable.relocated

        and:
        def copy = immutable.asMutable()
        copy != metadata
        copy.componentId == id
        copy.source == null
        copy.id == vid
        copy.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        copy.snapshotTimestamp == null
        copy.packaging == "jar"
        !copy.relocated
    }

    def "can override values from descriptor"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "1.2")
        def source = Stub(ModuleSource)
        def contentHash = new HashValue("123")

        def vid = Mock(ModuleVersionIdentifier)
        def metadata = new DefaultMutableMavenModuleResolveMetadata(vid, id, [])

        when:
        metadata.componentId = newId
        metadata.source = source
        metadata.status = "3"
        metadata.changing = true
        metadata.statusScheme = ["1", "2", "3"]
        metadata.snapshotTimestamp = "123"
        metadata.packaging = "pom"
        metadata.relocated = true
        metadata.contentHash = contentHash

        then:
        metadata.componentId == newId
        metadata.id == DefaultModuleVersionIdentifier.newId(newId)
        metadata.source == source
        metadata.changing
        metadata.status == "3"
        metadata.statusScheme == ["1", "2", "3"]
        metadata.snapshotTimestamp == "123"
        metadata.packaging == "pom"
        metadata.relocated
        metadata.contentHash == contentHash

        def immutable = metadata.asImmutable()
        immutable != metadata
        immutable.componentId == newId
        immutable.id == DefaultModuleVersionIdentifier.newId(newId)
        immutable.source == source
        immutable.status == "3"
        immutable.changing
        immutable.statusScheme == ["1", "2", "3"]
        immutable.snapshotTimestamp == "123"
        immutable.packaging == "pom"
        immutable.relocated
        immutable.contentHash == contentHash

        def copy = immutable.asMutable()
        copy != metadata
        copy.componentId == newId
        copy.id == DefaultModuleVersionIdentifier.newId(newId)
        copy.source == source
        copy.status == "3"
        copy.changing
        copy.statusScheme == ["1", "2", "3"]
        copy.snapshotTimestamp == "123"
        copy.packaging == "pom"
        copy.relocated
        copy.contentHash == contentHash
    }

    def "can attach variants"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [])

        given:
        def v1 = metadata.addVariant("api", attributes(usage: "compile"))
        v1.addFile("f1", "dir/f1")
        v1.addFile("f2.jar", "f2-1.2.jar")
        def v2 = metadata.addVariant("runtime", attributes(usage: "runtime"))
        v2.addFile("f1", "dir/f1")

        def immutable = metadata.asImmutable()

        expect:
        immutable.variants.size() == 2
        immutable.variants[0].name == "api"
        immutable.variants[0].asDescribable().displayName == "group:module:version variant api"
        immutable.variants[0].attributes == attributes(usage: "compile")
        immutable.variants[0].files.size() == 2
        immutable.variants[0].files[0].name == "f1"
        immutable.variants[0].files[0].uri == "dir/f1"
        immutable.variants[0].files[1].name == "f2.jar"
        immutable.variants[0].files[1].uri == "f2-1.2.jar"
        immutable.variants[1].name == "runtime"
        immutable.variants[1].asDescribable().displayName == "group:module:version variant runtime"
        immutable.variants[1].attributes == attributes(usage: "runtime")
        immutable.variants[1].files.size() == 1
        immutable.variants[1].files[0].name == "f1"
        immutable.variants[1].files[0].uri == "dir/f1"

        def immutable2 = immutable.asMutable().asImmutable()
        immutable2.variants.size() == 2
        immutable2.variants[0].name == "api"
        immutable2.variants[1].name == "runtime"

        def copy = immutable.asMutable()
        copy.addVariant("link", attributes())

        def immutable3 = copy.asImmutable()
        immutable3.variants.size() == 3
        immutable3.variants[0].name == "api"
        immutable3.variants[1].name == "runtime"
        immutable3.variants[2].name == "link"
        immutable3.variants[2].files.empty
    }

    def "variants are attached as children of configuration used for variant aware selection"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [])

        def attributes1 = attributes(usage: "compile")
        def attributes2 = attributes(usage: "runtime")

        def v1 = metadata.addVariant("api", attributes1)
        v1.addFile("f1.jar", "f1.jar")
        v1.addFile("f2.jar", "f2-1.2.jar")
        def v2 = metadata.addVariant("runtime", attributes2)
        v2.addFile("f2", "f2-version.zip")

        expect:
        def immutable = metadata.asImmutable()
        immutable.consumableConfigurationsHavingAttributes.size() == 1
        def defaultConfiguration = immutable.consumableConfigurationsHavingAttributes[0]
        defaultConfiguration.name == 'default'
        defaultConfiguration.variants.size() == 2

        defaultConfiguration.variants[0].asDescribable().displayName == "group:module:version variant api"
        defaultConfiguration.variants[0].attributes == attributes1
        defaultConfiguration.variants[0].artifacts.size() == 2
        def artifacts1 = defaultConfiguration.variants[0].artifacts as List
        artifacts1[0].name.name == 'f1'
        artifacts1[0].name.type == 'jar'
        artifacts1[0].name.classifier == null
        artifacts1[0].name.extension == 'jar'

        defaultConfiguration.variants[1].asDescribable().displayName == "group:module:version variant runtime"
        defaultConfiguration.variants[1].attributes == attributes2
        defaultConfiguration.variants[1].artifacts.size() == 1
        def artifacts2 = defaultConfiguration.variants[1].artifacts as List
        artifacts2[0].name.name == 'f2'
        artifacts2[0].name.type == 'zip'
        artifacts2[0].name.classifier == null
        artifacts2[0].name.extension == 'zip'
    }

    def "making changes to copy does not affect original"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "1.2")
        def source = Stub(ModuleSource)
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [])

        when:
        def immutable = metadata.asImmutable()
        def copy = immutable.asMutable()
        copy.componentId = newId
        copy.source = source
        copy.changing = true
        copy.status = "3"
        copy.statusScheme = ["2", "3"]
        copy.snapshotTimestamp = "123"
        copy.packaging = "pom"
        copy.relocated = true
        def immutableCopy = copy.asImmutable()

        then:
        metadata.componentId == id
        metadata.source == null
        metadata.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        metadata.snapshotTimestamp == null
        metadata.packaging == "jar"
        !metadata.relocated

        immutable.componentId == id
        immutable.source == null
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        immutable.snapshotTimestamp == null
        immutable.packaging == "jar"
        !immutable.relocated

        copy.componentId == newId
        copy.source == source
        copy.statusScheme == ["2", "3"]
        copy.snapshotTimestamp == "123"
        copy.packaging == "pom"
        copy.relocated

        immutableCopy.componentId == newId
        immutableCopy.source == source
        immutableCopy.statusScheme == ["2", "3"]
        immutableCopy.snapshotTimestamp == "123"
        immutableCopy.packaging == "pom"
        immutableCopy.relocated
    }

    def "making changes to original does not affect copy"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "1.2")
        def source = Stub(ModuleSource)
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [])

        when:
        def immutable = metadata.asImmutable()

        metadata.componentId = newId
        metadata.source = source
        metadata.statusScheme = ["1", "2"]
        metadata.snapshotTimestamp = "123"
        metadata.packaging = "pom"
        metadata.relocated = true

        def immutableCopy = metadata.asImmutable()

        then:
        metadata.componentId == newId
        metadata.source == source
        metadata.statusScheme == ["1", "2"]
        metadata.snapshotTimestamp == "123"
        metadata.packaging == "pom"
        metadata.relocated

        immutable.componentId == id
        immutable.source == null
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        immutable.snapshotTimestamp == null
        immutable.packaging == "jar"
        !immutable.relocated

        immutableCopy.componentId == newId
        immutableCopy.source == source
        immutableCopy.statusScheme == ["1", "2"]
        immutableCopy.snapshotTimestamp == "123"
        immutableCopy.packaging == "pom"
        immutableCopy.relocated
    }
}
