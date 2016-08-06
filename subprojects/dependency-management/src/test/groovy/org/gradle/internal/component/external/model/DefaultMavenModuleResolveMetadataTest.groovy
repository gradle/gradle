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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.model.DependencyMetadata

class DefaultMavenModuleResolveMetadataTest extends AbstractModuleComponentResolveMetadataTest {

    AbstractModuleComponentResolveMetadata createMetadata(ModuleComponentIdentifier id, ModuleDescriptorState moduleDescriptor) {
        return new DefaultMavenModuleResolveMetadata(new DefaultMutableMavenModuleResolveMetadata(id, moduleDescriptor, "pom", false))
    }

    def "can make a copy"() {
        def dependency1 = Stub(DependencyMetadata)
        def dependency2 = Stub(DependencyMetadata)

        given:
        def metadata = getMetadata()
        metadata.changing = true
        metadata.dependencies = [dependency1, dependency2]
        metadata.status = 'a'
        metadata.statusScheme = ['a', 'b', 'c']
        metadata.snapshotTimestamp = '123'

        when:
        def copy = metadata.copy()

        then:
        copy != metadata
        copy.descriptor == moduleDescriptor
        copy.changing
        copy.dependencies == [dependency1, dependency2]
        copy.status == 'a'
        copy.statusScheme == ['a', 'b', 'c']
        copy.packaging == "pom"
        !copy.relocated
        copy.snapshotTimestamp == '123'
    }

    def "recognises pom packaging"() {
        when:
        def metadata = new DefaultMavenModuleResolveMetadata(id, moduleDescriptor, packaging, false)

        then:
        metadata.packaging == packaging
        metadata.isPomPackaging() == isPom
        metadata.isKnownJarPackaging() == isJar

        where:
        packaging      | isPom | isJar
        "pom"          | true  | false
        "jar"          | false | true
        "war"          | false | false
        "maven-plugin" | false | true
    }
}
