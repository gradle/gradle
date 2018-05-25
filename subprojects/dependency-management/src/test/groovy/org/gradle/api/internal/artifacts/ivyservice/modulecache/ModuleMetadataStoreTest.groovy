/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.modulecache

import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.internal.SimpleMapInterner
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class ModuleMetadataStoreTest extends Specification {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    String repository = "repositoryId"
    ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock(ImmutableModuleIdentifierFactory) {
        module(_,_) >> { args -> DefaultModuleIdentifier.newId(*args)}
    }
    ModuleComponentIdentifier moduleComponentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.test", "testArtifact"), "1.0")
    ModuleMetadataSerializer serializer = Mock()
    @Subject ModuleMetadataStore store = new ModuleMetadataStore(temporaryFolder.getTestDirectory(), serializer, moduleIdentifierFactory, SimpleMapInterner.notThreadSafe())
    MavenMutableModuleMetadataFactory mavenMetadataFactory = new MavenMutableModuleMetadataFactory(moduleIdentifierFactory, TestUtil.attributesFactory(), TestUtil.objectInstantiator(), TestUtil.featurePreviews())

    def "getModuleDescriptorFile returns null for not cached descriptors"() {
        expect:
        null == store.getModuleDescriptor(new ModuleComponentAtRepositoryKey(repository, moduleComponentIdentifier))
    }

    def "putModuleDescriptor uses ModuleMetadataSerializer to write file"() {
        given:
        def descriptor = mavenMetadataFactory.create(moduleComponentIdentifier).asImmutable()

        when:
        store.putModuleDescriptor(new ModuleComponentAtRepositoryKey(repository, moduleComponentIdentifier), descriptor)

        then:
        1 * serializer.write(_, descriptor)
        temporaryFolder.file("org.test/testArtifact/1.0/repositoryId/descriptor.bin").assertExists()
    }

    def "getModuleDescriptor uses ModuleMetadataSerializer to read file and marks it as accessed"() {
        given:
        def descriptorFile = temporaryFolder.file("org.test/testArtifact/1.0/repositoryId/descriptor.bin").touch().makeOlder()
        def descriptor = mavenMetadataFactory.create(moduleComponentIdentifier)
        def beforeAccess = descriptorFile.lastModified()

        when:
        store.getModuleDescriptor(new ModuleComponentAtRepositoryKey(repository, moduleComponentIdentifier))

        then:
        1 * serializer.read(_, moduleIdentifierFactory) >> descriptor
        descriptorFile.parentFile.lastModified() > beforeAccess
    }
}
