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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.IvyModuleDescriptorWriter
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.internal.resource.local.PathKeyFileStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ModuleDescriptorStoreTest extends Specification {

    @Rule TestNameTestDirectoryProvider temporaryFolder
    ModuleDescriptorStore store
    PathKeyFileStore pathKeyFileStore = Mock()
    ModuleComponentRepository repository = Mock()
    LocallyAvailableResource fileStoreEntry = Mock()
    IvyModuleDescriptorWriter ivyModuleDescriptorWriter = Mock()
    IvyXmlModuleDescriptorParser ivyXmlModuleDescriptorParser = Mock()
    ModuleComponentIdentifier moduleComponentIdentifier = DefaultModuleComponentIdentifier.newId("org.test", "testArtifact", "1.0")
    ModuleDescriptor moduleDescriptor = new DefaultModuleDescriptor(IvyUtil.createModuleRevisionId(moduleComponentIdentifier), "Integration", new Date())

    def setup() {
        store = new ModuleDescriptorStore(pathKeyFileStore, ivyModuleDescriptorWriter, ivyXmlModuleDescriptorParser);
        _ * repository.getId() >> "repositoryId"
    }

    def "getModuleDescriptorFile returns null for not cached descriptors"() {
        when:
        pathKeyFileStore.get("org.test/testArtifact/1.0/repositoryId/ivy.xml") >> null
        then:
        null == store.getModuleDescriptor(repository, moduleComponentIdentifier)
    }

    def "getModuleDescriptorFile uses PathKeyFileStore to get file"() {
        when:
        store.getModuleDescriptor(repository, moduleComponentIdentifier);
        then:
        1 * pathKeyFileStore.get("org.test/testArtifact/1.0/repositoryId/ivy.xml") >> null
    }

    def "putModuleDescriptor uses PathKeyFileStore to write file"() {
        setup:
        File descriptorFile = temporaryFolder.createFile("fileStoreEntry")
        def descriptor = new ModuleDescriptorState(moduleDescriptor)
        when:
        store.putModuleDescriptor(repository, moduleComponentIdentifier, descriptor);
        then:
        1 * pathKeyFileStore.add("org.test/testArtifact/1.0/repositoryId/ivy.xml", _) >> { path, action ->
            action.execute(descriptorFile); fileStoreEntry
        };
        1 * ivyModuleDescriptorWriter.write(descriptor, descriptorFile)
    }
}
