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


package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.descriptor.Configuration
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ModuleSource

class DefaultMavenModuleResolveMetadataTest extends AbstractModuleComponentResolveMetadataTest {
    @Override
    AbstractModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, ModuleDescriptorState moduleDescriptor, List<Configuration> configurations, List<DependencyMetadata> dependencies) {
        return new DefaultMavenModuleResolveMetadata(new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, moduleDescriptor, "pom", false, dependencies))
    }

    def "copy with different source"() {
        given:
        def source = Stub(ModuleSource)
        def mutable = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [] as Set)
        mutable.packaging = "other"
        mutable.relocated = true
        mutable.snapshotTimestamp = "123"
        def metadata = mutable.asImmutable()

        when:
        def copy = metadata.withSource(source)

        then:
        copy.source == source
        copy.packaging == "other"
        copy.relocated
        copy.snapshotTimestamp == "123"
    }

    def "recognises pom packaging"() {
        when:
        def metadata = new DefaultMutableMavenModuleResolveMetadata(Mock(ModuleVersionIdentifier), id, [] as Set)
        metadata.packaging = packaging

        then:
        metadata.packaging == packaging
        metadata.pomPackaging == isPom
        metadata.knownJarPackaging == isJar

        where:
        packaging      | isPom | isJar
        "pom"          | true  | false
        "jar"          | false | true
        "war"          | false | false
        "maven-plugin" | false | true
    }
}
