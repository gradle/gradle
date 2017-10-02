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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.hash.HashValue
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.internal.component.external.model.AbstractMutableModuleComponentResolveMetadata.EMPTY_CONTENT

abstract class AbstractMutableModuleComponentResolveMetadataTest extends Specification {
    def id = DefaultModuleComponentIdentifier.newId("group", "module", "version")
    def configurations = []
    def dependencies = []

    abstract AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, List<Configuration> configurations, List<DependencyMetadata> dependencies)

    abstract AbstractMutableModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id);

    MutableModuleComponentResolveMetadata getMetadata() {
        return createMetadata(id, configurations, dependencies)
    }

    def "can replace identifiers"() {
        def newId = DefaultModuleComponentIdentifier.newId("group", "module", "version")
        def metadata = getMetadata()

        given:
        metadata.componentId = newId

        expect:
        metadata.componentId == newId
        metadata.id == DefaultModuleVersionIdentifier.newId(newId)
        metadata.asImmutable().componentId == newId
        metadata.asImmutable().asMutable().componentId == newId
    }

    def "builds and caches the dependency meta-data"() {
        given:
        configuration("compile")
        configuration("runtime", ["compile"])
        dependency("org", "module", "1.2", ["compile"])
        dependency("org", "another", "1.2", ["runtime"])

        when:
        def deps = metadata.dependencies

        then:
        deps.size() == 2
        deps[0].requested == newSelector("org", "module", "1.2")
        deps[1].requested == newSelector("org", "another", "1.2")

        and:
        def immutable = metadata.asImmutable()
        immutable.dependencies.size() == 2
        immutable.dependencies[0].requested == newSelector("org", "module", "1.2")
        immutable.dependencies[1].requested == newSelector("org", "another", "1.2")
        immutable.getConfiguration("compile").dependencies.size() == 1
        immutable.getConfiguration("compile").dependencies[0] == immutable.dependencies[0]
        immutable.getConfiguration("runtime").dependencies.size() == 2
        immutable.getConfiguration("runtime").dependencies[0] == immutable.dependencies[0]
        immutable.getConfiguration("runtime").dependencies[1] == immutable.dependencies[1]

        and:
        def copy = immutable.asMutable()
        copy.dependencies.size() == 2
        copy.dependencies[0].requested == newSelector("org", "module", "1.2")
        copy.dependencies[1].requested == newSelector("org", "another", "1.2")

        and:
        def immutable2 = copy.asImmutable()
        immutable2.dependencies.size() == 2
        immutable2.dependencies[0].requested == newSelector("org", "module", "1.2")
        immutable2.dependencies[1].requested == newSelector("org", "another", "1.2")
        immutable2.getConfiguration("compile").dependencies.size() == 1
        immutable2.getConfiguration("compile").dependencies[0] == immutable.dependencies[0]
        immutable2.getConfiguration("runtime").dependencies.size() == 2
        immutable2.getConfiguration("runtime").dependencies[0] == immutable.dependencies[0]
        immutable2.getConfiguration("runtime").dependencies[1] == immutable.dependencies[1]
    }

    def "can create default metadata"() {
        def metadata = createMetadata(id)

        expect:
        metadata.componentId == id
        metadata.dependencies.empty
        !metadata.changing
        !metadata.missing
        metadata.status == "integration"
        metadata.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        metadata.contentHash == EMPTY_CONTENT

        def immutable = metadata.asImmutable()
        immutable.componentId == id
        !immutable.changing
        !immutable.missing
        immutable.status == "integration"
        immutable.statusScheme == ComponentResolveMetadata.DEFAULT_STATUS_SCHEME
        immutable.contentHash == EMPTY_CONTENT
        immutable.getConfiguration("default")
        immutable.getConfiguration("default").artifacts.size() == 1
        immutable.getConfiguration("default").artifacts.first().name.name == id.module
        immutable.getConfiguration("default").artifacts.first().name.classifier == null
        immutable.getConfiguration("default").artifacts.first().name.extension == 'jar'
        immutable.getConfiguration("default").artifacts.first().name.extension == 'jar'
        immutable.dependencies.empty
    }

    def "can override default values"() {
        def contentHash = new HashValue("123")

        def metadata = createMetadata(id)

        given:
        metadata.changing = true
        metadata.missing = true
        metadata.status = "broken"
        metadata.contentHash = contentHash

        expect:
        def immutable = metadata.asImmutable()
        immutable.changing
        immutable.missing
        immutable.status == "broken"
        immutable.contentHash == contentHash

        def copy = immutable.asMutable()
        copy.changing
        copy.missing
        copy.status == "broken"
        copy.contentHash == contentHash

        def immutable2 = copy.asImmutable()
        immutable2.changing
        immutable2.missing
        immutable2.status == "broken"
        immutable2.contentHash == contentHash
    }

    def "can changes to mutable metadata does not affect copies"() {
        def contentHash = new HashValue("123")
        def newContentHash = new HashValue("234")

        def metadata = createMetadata(id)

        given:
        metadata.changing = true
        metadata.missing = true
        metadata.status = "broken"
        metadata.contentHash = contentHash

        def immutable = metadata.asImmutable()

        metadata.changing = false
        metadata.missing = false
        metadata.status = "ok"
        metadata.contentHash = newContentHash

        expect:
        immutable.changing
        immutable.missing
        immutable.status == "broken"
        immutable.contentHash == contentHash

        def copy = immutable.asMutable()
        copy.changing
        copy.missing
        copy.status == "broken"
        copy.contentHash == contentHash
    }

    def "can replace the dependencies for the component"() {
        when:
        configuration("compile")
        configuration("runtime", ["compile"])

        dependency("foo", "bar", "1.0")
        def metadata = getMetadata()

        then:
        metadata.dependencies*.requested*.toString() == ["foo:bar:1.0"]

        when:
        def dependency1 = dependency("foo", "bar", "1.2", ["runtime"])
        def dependency2 = dependency("foo", "baz", "1.2", ["compile"])
        metadata.dependencies = [dependency1, dependency2]

        then:
        metadata.dependencies == [dependency1, dependency2]

        def immutable = metadata.asImmutable()
        immutable.getConfiguration("compile").dependencies == [dependency2]
        immutable.getConfiguration("runtime").dependencies == [dependency1, dependency2]

        when:
        def copy = immutable.asMutable()
        copy.dependencies = [dependency1]

        then:
        def immutable2 = copy.asImmutable()
        immutable2.getConfiguration("compile").dependencies == []
        immutable2.getConfiguration("runtime").dependencies == [dependency1]
    }

    def "can replace the artifacts for the component"() {
        when:
        configuration("runtime")
        def metadata = getMetadata()
        def a1 = metadata.artifact("jar", "jar", null)
        def a2 = metadata.artifact("pom", "pom", null)
        metadata.artifactOverrides = [a1, a2]

        then:
        def immutable = metadata.asImmutable()
        immutable.artifactOverrides == [a1, a2]
        immutable.getConfiguration("runtime").artifacts == [a1, a2] as Set

        def copy = immutable.asMutable()
        copy.artifactOverrides == [a1, a2]

        when:
        metadata.artifactOverrides = [a2]

        then:
        def immutable2 = metadata.asImmutable()
        immutable2.artifactOverrides == [a2]
        immutable2.getConfiguration("runtime").artifacts == [a2] as Set
    }

    def dependency(String org, String module, String version, List<String> confs = []) {
        def builder = ImmutableListMultimap.builder()
        confs.each { builder.put(it, it) }
        def dependency = new IvyDependencyMetadata(newSelector(org, module, version), builder.build())
        dependencies.add(dependency)
        return dependency
    }

    def configuration(String name, List<String> extendsFrom = []) {
        configurations.add(new Configuration(name, true, true, extendsFrom))
    }

    def attributes(Map<String, String> values) {
        def attrs = ImmutableAttributes.EMPTY
        if (values) {
            values.each { String key, String value ->
                attrs = TestUtil.attributesFactory().concat(attrs, Attribute.of(key, String), value)
            }
        }
        return attrs
    }
}
