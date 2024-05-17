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

import com.google.common.collect.ImmutableListMultimap
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.util.AttributeTestUtil
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

abstract class AbstractMutableModuleComponentResolveMetadataTest extends Specification {
    def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
    def configurations = []
    def dependencies = []

    static VersionConstraint v(String version) {
        new DefaultMutableVersionConstraint(version)
    }

    abstract AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List<DependencyMetadata> dependencies)

    abstract AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id);

    MutableModuleComponentResolveMetadata getMetadata() {
        return createMetadata(id, configurations, dependencies)
    }

    def "can replace identifiers"() {
        def newId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def metadata = getMetadata()

        given:
        metadata.id = newId

        expect:
        metadata.id == newId
        metadata.moduleVersionId == DefaultModuleVersionIdentifier.newId(newId)
        metadata.asImmutable().id == newId
        metadata.asImmutable().asMutable().id == newId
    }

    def "can create default metadata"() {
        def metadata = createMetadata(id)

        expect:
        metadata.id == id
        metadata.dependencies.empty
        !metadata.changing
        !metadata.missing
        metadata.status == "integration"
        metadata.statusScheme == ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME


        def immutable = metadata.asImmutable()
        immutable.id == id
        !immutable.changing
        !immutable.missing
        immutable.status == "integration"
        immutable.statusScheme == ExternalComponentResolveMetadata.DEFAULT_STATUS_SCHEME

        immutable.getConfiguration("default")
        immutable.getConfiguration("default").artifacts.size() == 1
        immutable.getConfiguration("default").artifacts.first().name.name == id.module
        immutable.getConfiguration("default").artifacts.first().name.classifier == null
        immutable.getConfiguration("default").artifacts.first().name.extension == 'jar'
        immutable.getConfiguration("default").artifacts.first().name.extension == 'jar'
        immutable.dependencies.empty
    }

    def "can override default values"() {
        def metadata = createMetadata(id)

        given:
        metadata.changing = true
        metadata.missing = true
        metadata.status = "broken"

        expect:
        def immutable = metadata.asImmutable()
        immutable.changing
        immutable.missing
        immutable.status == "broken"

        def copy = immutable.asMutable()
        copy.changing
        copy.missing
        copy.status == "broken"

        def immutable2 = copy.asImmutable()
        immutable2.changing
        immutable2.missing
        immutable2.status == "broken"
    }

    def "can changes to mutable metadata does not affect copies"() {

        def metadata = createMetadata(id)

        given:
        metadata.changing = true
        metadata.missing = true
        metadata.status = "broken"

        def immutable = metadata.asImmutable()

        metadata.changing = false
        metadata.missing = false
        metadata.status = "ok"

        expect:
        immutable.changing
        immutable.missing
        immutable.status == "broken"

        def copy = immutable.asMutable()
        copy.changing
        copy.missing
        copy.status == "broken"
    }

    def "can attach variants with files"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def metadata = createMetadata(id)

        given:
        def v1 = metadata.addVariant("api", attributes(usage: "compile"),)
        v1.addFile("f1", "dir/f1")
        v1.addFile("f2.jar", "f2-1.2.jar")
        def v2 = metadata.addVariant("runtime", attributes(usage: "runtime"),)
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
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def metadata = createMetadata(id)

        given:
        def v1 = metadata.addVariant("api", attributes(usage: "compile"))
        v1.addDependency("g1", "m1", v("v1"), [], null, ImmutableAttributes.EMPTY, [], false, null)
        v1.addDependency("g2", "m2", v("v2"), [], "v2 is tested", ImmutableAttributes.EMPTY, [], true, null)
        def v2 = metadata.addVariant("runtime", attributes(usage: "runtime"))
        v2.addDependency("g1", "m1", v("v1"), [], null, ImmutableAttributes.EMPTY, [], false, null)

        expect:
        metadata.variants.size() == 2
        metadata.variants[0].dependencies.size() == 2
        metadata.variants[0].dependencies[0].group == "g1"
        metadata.variants[0].dependencies[0].module == "m1"
        metadata.variants[0].dependencies[0].versionConstraint.requiredVersion == "v1"
        !metadata.variants[0].dependencies[0].endorsingStrictVersions
        metadata.variants[0].dependencies[1].group == "g2"
        metadata.variants[0].dependencies[1].module == "m2"
        metadata.variants[0].dependencies[1].versionConstraint.requiredVersion == "v2"
        metadata.variants[0].dependencies[1].reason == "v2 is tested"
        metadata.variants[0].dependencies[1].endorsingStrictVersions
        metadata.variants[1].dependencies.size() == 1
        metadata.variants[1].dependencies[0].group == "g1"
        metadata.variants[1].dependencies[0].module == "m1"
        metadata.variants[1].dependencies[0].versionConstraint.requiredVersion == "v1"
        !metadata.variants[1].dependencies[0].endorsingStrictVersions

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
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def metadata = createMetadata(id)

        def attributes1 = attributes(usage: "compile")
        def attributes2 = attributes(usage: "runtime")

        def v1 = metadata.addVariant("api", attributes1,)
        v1.addFile("f1.jar", "f1.jar")
        v1.addFile("f2.jar", "f2-1.2.jar")
        v1.addDependency("g1", "m1", v("v1"), [], null, ImmutableAttributes.EMPTY, [], false, null)
        def v2 = metadata.addVariant("runtime", attributes2,)
        v2.addFile("f2", "f2-version.zip")
        v2.addDependency("g2", "m2", v("v2"), [], null, ImmutableAttributes.EMPTY, [], false, null)
        v2.addDependency("g3", "m3", v("v3"), [], null, ImmutableAttributes.EMPTY, [], false, null)

        expect:
        def immutable = metadata.asImmutable()
        def variantsForTraversal = immutable.getVariantsForGraphTraversal()
        variantsForTraversal.size() == 2
        variantsForTraversal[0].name == 'api'
        variantsForTraversal[0].dependencies.size() == 1
        variantsForTraversal[1].name == 'runtime'
        variantsForTraversal[1].dependencies.size() == 2

        def api = variantsForTraversal[0]
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

        def runtime = variantsForTraversal[1]
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
    }


    def dependency(String org, String module, String version, List<String> confs = []) {
        def builder = ImmutableListMultimap.builder()
        confs.each { builder.put(it, it) }
        def dependency = new IvyDependencyDescriptor(newSelector(DefaultModuleIdentifier.newId(org, module), v(version)), builder.build())
        dependencies.add(dependency)
        return dependency
    }

    def configuration(String name, List<String> extendsFrom = []) {
        configurations.add(new Configuration(name, true, true, extendsFrom))
    }

    def attributes(Map<String, String> values) {
        def attrs = AttributeTestUtil.attributesFactory().mutable()
        attrs.attribute(ProjectInternal.STATUS_ATTRIBUTE, 'integration')
        if (values) {
            values.each { String key, String value ->
                attrs.attribute(Attribute.of(key, String), value)
            }
        }
        return attrs.asImmutable()
    }
}
