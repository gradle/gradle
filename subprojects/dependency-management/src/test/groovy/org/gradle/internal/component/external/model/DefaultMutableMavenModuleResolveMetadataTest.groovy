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

import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ModuleSource
import org.gradle.internal.hash.HashValue
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser

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
        immutable.getConfiguration("compile").artifacts.size() == 1
        immutable.getConfiguration("runtime").artifacts.size() == 1
        immutable.getConfiguration("default").artifacts.size() == 1
        immutable.getConfiguration("master").artifacts.empty

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

        and:
        def immutable2 = copy.asImmutable()
        immutable2.getConfiguration("compile").artifacts.size() == 1
        immutable2.getConfiguration("runtime").artifacts.size() == 1
        immutable2.getConfiguration("default").artifacts.size() == 1
        immutable2.getConfiguration("master").artifacts.empty
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

    def "can attach variants with files"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [])

        given:
        def v1 = metadata.addVariant("api", attributes(usage: "compile"))
        v1.addFile("f1", "dir/f1")
        v1.addFile("f2.jar", "f2-1.2.jar")
        def v2 = metadata.addVariant("runtime", attributes(usage: "runtime"))
        v2.addFile("f1", "dir/f1")

        expect:
        metadata.variants.size() == 2
        metadata.variants[0].name == "api"
        metadata.variants[0].asDescribable().displayName == "group:module:version variant api"
        metadata.variants[0].attributes == attributes(usage: "compile")
        metadata.variants[0].files.size() == 2
        metadata.variants[0].files[0].name == "f1"
        metadata.variants[0].files[0].uri == "dir/f1"
        metadata.variants[0].files[1].name == "f2.jar"
        metadata.variants[0].files[1].uri == "f2-1.2.jar"
        metadata.variants[1].name == "runtime"
        metadata.variants[1].asDescribable().displayName == "group:module:version variant runtime"
        metadata.variants[1].attributes == attributes(usage: "runtime")
        metadata.variants[1].files.size() == 1
        metadata.variants[1].files[0].name == "f1"
        metadata.variants[1].files[0].uri == "dir/f1"

        def immutable = metadata.asImmutable()
        immutable.variants.size() == 2
        immutable.variants[0].name == "api"
        immutable.variants[0].files.size() == 2
        immutable.variants[1].name == "runtime"
        immutable.variants[1].files.size() == 1

        def metadata2 = immutable.asMutable()
        metadata2.variants.size() == 2
        metadata2.variants[0].name == "api"
        metadata2.variants[0].files.size() == 2
        metadata2.variants[1].name == "runtime"
        metadata2.variants[1].files.size() == 1

        def immutable2 = metadata2.asImmutable()
        immutable2.variants.size() == 2
        immutable2.variants[0].name == "api"
        immutable2.variants[0].files.size() == 2
        immutable2.variants[1].name == "runtime"
        immutable2.variants[1].files.size() == 1

        def copy = immutable.asMutable()
        copy.addVariant("link", attributes())

        copy.variants.size() == 3
        copy.variants[0].name == "api"
        copy.variants[0].files.size() == 2
        copy.variants[1].name == "runtime"
        copy.variants[1].files.size() == 1
        copy.variants[2].name == "link"
        copy.variants[2].files.empty

        def immutable3 = copy.asImmutable()
        immutable3.variants.size() == 3
        immutable3.variants[0].name == "api"
        immutable3.variants[0].files.size() == 2
        immutable3.variants[1].name == "runtime"
        immutable3.variants[1].files.size() == 1
        immutable3.variants[2].name == "link"
        immutable3.variants[2].files.empty
    }

    def "can attach variants with dependencies"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [])

        given:
        def v1 = metadata.addVariant("api", attributes(usage: "compile"))
        v1.addDependency("g1", "m1", "v1")
        v1.addDependency("g2", "m2", "v2")
        def v2 = metadata.addVariant("runtime", attributes(usage: "runtime"))
        v2.addDependency("g1", "m1", "v1")

        expect:
        metadata.variants.size() == 2
        metadata.variants[0].dependencies.size() == 2
        metadata.variants[0].dependencies[0].group == "g1"
        metadata.variants[0].dependencies[0].module == "m1"
        metadata.variants[0].dependencies[0].version == "v1"
        metadata.variants[0].dependencies[1].group == "g2"
        metadata.variants[0].dependencies[1].module == "m2"
        metadata.variants[0].dependencies[1].version == "v2"
        metadata.variants[1].dependencies.size() == 1
        metadata.variants[1].dependencies[0].group == "g1"
        metadata.variants[1].dependencies[0].module == "m1"
        metadata.variants[1].dependencies[0].version == "v1"

        def immutable = metadata.asImmutable()
        immutable.variants.size() == 2
        immutable.variants[0].dependencies.size() == 2
        immutable.variants[1].dependencies.size() == 1

        def immutable2 = immutable.asMutable().asImmutable()
        immutable2.variants[0].dependencies.size() == 2
        immutable2.variants[1].dependencies.size() == 1

        def copy = immutable.asMutable()
        copy.variants.size() == 2
        copy.addVariant("link", attributes())
        copy.variants.size() == 3

        def immutable3 = copy.asImmutable()
        immutable3.variants.size() == 3
        immutable3.variants[0].dependencies.size() == 2
        immutable3.variants[1].dependencies.size() == 1
        immutable3.variants[2].dependencies.empty
    }

    def "variants are attached as consumable configurations used for variant aware selection"() {
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [])

        def attributes1 = attributes(usage: "compile")
        def attributes2 = attributes(usage: "runtime")

        def v1 = metadata.addVariant("api", attributes1)
        v1.addFile("f1.jar", "f1.jar")
        v1.addFile("f2.jar", "f2-1.2.jar")
        v1.addDependency("g1", "m1", "v1")
        def v2 = metadata.addVariant("runtime", attributes2)
        v2.addFile("f2", "f2-version.zip")
        v2.addDependency("g2", "m2", "v2")
        v2.addDependency("g3", "m3", "v3")

        expect:
        metadata.variantsForGraphTraversal.size() == 2
        metadata.variantsForGraphTraversal[0].name == 'api'
        metadata.variantsForGraphTraversal[0].dependencies.size() == 1
        metadata.variantsForGraphTraversal[1].name == 'runtime'
        metadata.variantsForGraphTraversal[1].dependencies.size() == 2

        def immutable = metadata.asImmutable()
        immutable.variantsForGraphTraversal.size() == 2

        def api = immutable.variantsForGraphTraversal[0]
        api.name == 'api'
        api.asDescribable().displayName == 'group:module:version variant api'
        api.attributes == attributes1

        api.dependencies.size() == 1
        api.dependencies[0].selector.group == "g1"
        api.dependencies[0].selector.module == "m1"
        api.dependencies[0].selector.version == "v1"

        api.variants.size() == 1
        api.variants[0].asDescribable().displayName == "group:module:version variant api"
        api.variants[0].attributes == attributes1
        api.variants[0].artifacts.size() == 2
        def artifacts1 = api.variants[0].artifacts as List
        artifacts1[0].name.name == 'f1'
        artifacts1[0].name.type == 'jar'
        artifacts1[0].name.classifier == null
        artifacts1[0].name.extension == 'jar'

        def runtime = immutable.variantsForGraphTraversal[1]
        runtime.name == 'runtime'
        runtime.asDescribable().displayName == 'group:module:version variant runtime'
        runtime.attributes == attributes2
        runtime.variants.size() == 1

        runtime.dependencies.size() == 2

        runtime.variants[0].asDescribable().displayName == "group:module:version variant runtime"
        runtime.variants[0].attributes == attributes2
        runtime.variants[0].artifacts.size() == 1
        def artifacts2 = runtime.variants[0].artifacts as List
        artifacts2[0].name.name == 'f2'
        artifacts2[0].name.type == 'zip'
        artifacts2[0].name.classifier == null
        artifacts2[0].name.extension == 'zip'

        def copy = immutable.asMutable()
        copy.variantsForGraphTraversal.size() == 2
        copy.addVariant("link", attributes())
        copy.variantsForGraphTraversal.size() == 3

        def immutable2 = copy.asImmutable()
        immutable2.variantsForGraphTraversal.size() == 3
    }

    def "resets variant backed configuration metadata if dependency rules are modified"() {
        given:
        def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [])
        metadata.addVariant("aVariant", Mock(ImmutableAttributes))
        def before = metadata.variantsForGraphTraversal

        when:
        metadata.addDependencyMetadataRule("aVariant", Mock(Action), Mock(Instantiator), Mock(NotationParser))
        def after = metadata.variantsForGraphTraversal

        then:
        !after.is(before)
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
