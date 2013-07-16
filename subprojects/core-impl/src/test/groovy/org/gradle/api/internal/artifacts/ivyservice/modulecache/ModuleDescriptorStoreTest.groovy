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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.IvyModuleDescriptorWriter
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser
import org.gradle.api.internal.filestore.PathKeyFileStore
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ModuleDescriptorStoreTest extends Specification {

    @Rule TestNameTestDirectoryProvider temporaryFolder
    ModuleDescriptorStore store
    PathKeyFileStore pathKeyFileStore = Mock()
    ModuleRevisionId moduleRevisionId = Mock()
    ModuleVersionRepository repository = Mock()
    LocallyAvailableResource fileStoreEntry = Mock()
    ModuleDescriptor moduleDescriptor = Mock()
    IvyModuleDescriptorWriter ivyModuleDescriptorWriter = Mock()
    IvyXmlModuleDescriptorParser ivyXmlModuleDescriptorParser = Mock()
    ModuleVersionIdentifier moduleVersionIdentifier = Mock()

    def setup() {
        store = new ModuleDescriptorStore(pathKeyFileStore, ivyModuleDescriptorWriter, ivyXmlModuleDescriptorParser);
        _ * repository.getId() >> "repositoryId"
        _ * moduleVersionIdentifier.group >> "org.test"
        _ * moduleVersionIdentifier.name >> "testArtifact"
        _ * moduleVersionIdentifier.version >> "1.0"
        _ * moduleDescriptor.getModuleRevisionId() >> moduleRevisionId
    }

    def "getModuleDescriptorFile returns null for not cached descriptors"() {
        when:
        pathKeyFileStore.get("module-metadata/org.test/testArtifact/1.0/repositoryId/ivy.xml") >> null
        then:
        null == store.getModuleDescriptor(repository, moduleVersionIdentifier)
    }

    def "getModuleDescriptorFile uses PathKeyFileStore to get file"() {
        when:
        store.getModuleDescriptor(repository, moduleVersionIdentifier);
        then:
        1 * pathKeyFileStore.get("module-metadata/org.test/testArtifact/1.0/repositoryId/ivy.xml") >> null
    }

    def "putModuleDescriptor uses PathKeyFileStore to write file"() {
        setup:
        _ * moduleRevisionId.organisation >> "org.test"
        _ * moduleRevisionId.name >> "testArtifact"
        _ * moduleRevisionId.revision >> "1.0"
        File descriptorFile = temporaryFolder.createFile("fileStoreEntry")
        when:
        store.putModuleDescriptor(repository, moduleDescriptor);
        then:
        1 * pathKeyFileStore.add("module-metadata/org.test/testArtifact/1.0/repositoryId/ivy.xml", _) >> { path, action ->
            action.execute(descriptorFile); fileStoreEntry
        };
        1 * ivyModuleDescriptorWriter.write(moduleDescriptor, descriptorFile)
    }
}