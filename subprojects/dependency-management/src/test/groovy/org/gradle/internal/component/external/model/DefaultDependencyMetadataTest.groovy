/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.local.model.TestComponentIdentifiers
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

abstract class DefaultDependencyMetadataTest extends Specification {
    def attributesSchema = Stub(AttributesSchemaInternal)

    def requested = newSelector("org", "module", v("1.2+"))
    def id = DefaultModuleVersionIdentifier.newId("org", "module", "1.2+")

    static VersionConstraint v(String version) {
        new DefaultMutableVersionConstraint(version)
    }

    abstract DefaultDependencyMetadata create(ModuleComponentSelector selector)

    abstract DefaultDependencyMetadata createWithArtifacts(ModuleComponentSelector selector, List<Artifact> artifacts)

    def "creates a copy with new requested version"() {
        def metadata = create(requested)

        given:

        when:
        def copy = metadata.withRequestedVersion(v("1.3+"))
        def expected = newSelector("org", "module", v("1.3+"))

        then:
        copy != metadata
        copy.selector == expected
    }

    def "returns this if new requested version is the same as current requested version"() {
        def metadata = create(requested)

        expect:
        metadata.withRequestedVersion(v("1.2+")).is(metadata)
        metadata.withTarget(newSelector("org", "module", v("1.2+"))).is(metadata)
    }

    def "creates a copy with new requested project selector"() {
        def metadata = create(requested)
        def selector = TestComponentIdentifiers.newSelector(":project")

        when:
        def copy = metadata.withTarget(selector)

        then:
        copy != metadata
        copy.selector == selector
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts"() {
        def metadata = createWithArtifacts(requested, [])
        def fromConfiguration = Stub(ConfigurationMetadata)

        expect:
        metadata.getConfigurationArtifacts(fromConfiguration).empty
    }

    def "uses artifacts defined by dependency descriptor"() {
        def artifact1 = new Artifact(new DefaultIvyArtifactName("art1", "type", "ext"), ["config"] as Set)
        def artifact2 = new Artifact(new DefaultIvyArtifactName("art2", "type", "ext"), ["other"] as Set)
        def artifact3 = new Artifact(new DefaultIvyArtifactName("art3", "type", "ext"), ["super"] as Set)

        given:
        def metadata = createWithArtifacts(requested, [artifact1, artifact2, artifact3])

        expect:
        metadata.dependencyArtifacts.size() == 3
        def artifacts = metadata.dependencyArtifacts
        artifacts[0] == artifact1
        artifacts[1] == artifact2
        artifacts[2] == artifact3
    }

    def "returns a module component selector if descriptor indicates a default dependency"() {
        given:
        def metadata = create(requested)

        when:
        ComponentSelector componentSelector = metadata.getSelector()

        then:
        componentSelector instanceof ModuleComponentSelector
        componentSelector.group == 'org'
        componentSelector.module == 'module'
        componentSelector.version == '1.2+'
    }

    def configuration(String name, String... parents) {
        def config = Stub(ConfigurationMetadata)
        config.hierarchy >> ([name] as Set) + (parents as Set)
        return config
    }
}
