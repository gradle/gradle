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

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.internal.resource.local.PathKeyFileStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.SimpleMapInterner
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class ModuleMetadataStoreTest extends Specification {

    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    def pathKeyFileStore = Mock(PathKeyFileStore)
    def repository = "repositoryId"
    def fileStoreEntry = Mock(LocallyAvailableResource)
    def moduleIdentifierFactory = Mock(ImmutableModuleIdentifierFactory) {
        module(_,_) >> { args -> DefaultModuleIdentifier.newId(*args)}
    }
    def moduleComponentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org.test", "testArtifact"), "1.0")
    def serializer = Mock(ModuleMetadataSerializer)
    @Subject ModuleMetadataStore store = new ModuleMetadataStore(pathKeyFileStore, serializer, moduleIdentifierFactory, SimpleMapInterner.notThreadSafe())
    def mavenMetadataFactory = DependencyManagementTestUtil.mavenMetadataFactory()

    def "getModuleDescriptorFile returns null for not cached descriptors"() {
        when:
        pathKeyFileStore.get("org.test/testArtifact/1.0/repositoryId/descriptor.bin") >> null
        then:
        null == store.getModuleDescriptor(new ModuleComponentAtRepositoryKey(repository, moduleComponentIdentifier))
    }

    def "getModuleDescriptorFile uses PathKeyFileStore to get file"() {
        when:
        store.getModuleDescriptor(new ModuleComponentAtRepositoryKey(repository, moduleComponentIdentifier))
        then:
        1 * pathKeyFileStore.get("org.test", "testArtifact", "1.0", "repositoryId", "descriptor.bin") >> null
    }

    def "putModuleDescriptor uses PathKeyFileStore to write file"() {
        setup:
        File descriptorFile = temporaryFolder.createFile("fileStoreEntry")
        def descriptor = mavenMetadataFactory.create(moduleComponentIdentifier, []).asImmutable()

        when:
        store.putModuleDescriptor(new ModuleComponentAtRepositoryKey(repository, moduleComponentIdentifier), descriptor)
        then:
        1 * pathKeyFileStore.add("org.test/testArtifact/1.0/repositoryId/descriptor.bin", _) >> { path, action ->
            action.execute(descriptorFile); fileStoreEntry
        };
        1 * serializer.write(_, descriptor, _)
    }
}
