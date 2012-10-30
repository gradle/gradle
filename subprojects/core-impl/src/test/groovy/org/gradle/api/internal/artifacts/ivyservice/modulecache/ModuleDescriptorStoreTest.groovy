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
import org.apache.ivy.plugins.parser.xml.XmlModuleDescriptorParser
import org.gradle.api.internal.artifacts.ivyservice.IvyModuleDescriptorWriter
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository
import org.gradle.api.internal.filestore.FileStoreEntry
import org.gradle.api.internal.filestore.PathKeyFileStore
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class ModuleDescriptorStoreTest extends Specification {

    @Rule TemporaryFolder temporaryFolder
    ModuleDescriptorStore store
    PathKeyFileStore pathKeyFileStore = Mock()
    ModuleRevisionId moduleRevisionId = Mock()
    ModuleVersionRepository repository = Mock()
    FileStoreEntry fileStoreEntry = Mock()
    ModuleDescriptor moduleDescriptor = Mock()
    IvyModuleDescriptorWriter ivyModuleDescriptorWriter = Mock()
    XmlModuleDescriptorParser xmlModuleDescriptorParser = Mock()

    def setup() {
        store = new ModuleDescriptorStore(pathKeyFileStore, ivyModuleDescriptorWriter, xmlModuleDescriptorParser);
        _ * repository.getId() >> "repositoryId"
        _ * moduleRevisionId.getOrganisation() >> "org.test"
        _ * moduleRevisionId.getName() >> "testArtifact"
        _ * moduleRevisionId.getRevision() >> "1.0"
        _ * moduleDescriptor.getModuleRevisionId() >> moduleRevisionId
    }

    def "getModuleDescriptorFile returns null for not cached descriptors"() {
        when:
        pathKeyFileStore.get("module-metadata/org.test/testArtifact/1.0/repositoryId.ivy.xml") >> null
        then:
        null == store.getModuleDescriptor(repository, moduleRevisionId)
    }

    def "getModuleDescriptorFile uses PathKeyFileStore to get file"() {
        when:
        store.getModuleDescriptor(repository, moduleRevisionId);
        then:
        1 * pathKeyFileStore.get("module-metadata/org.test/testArtifact/1.0/repositoryId.ivy.xml") >> null
    }

    def "putModuleDescriptor uses PathKeyFileStore to write file"() {
        setup:
        File descriptorFile = temporaryFolder.createFile("fileStoreEntry")
        when:
        store.putModuleDescriptor(repository, moduleDescriptor);
        then:
        1 * pathKeyFileStore.add("module-metadata/org.test/testArtifact/1.0/repositoryId.ivy.xml", _) >> {path, action ->
            action.execute(descriptorFile); fileStoreEntry
        };
        1 * ivyModuleDescriptorWriter.write(moduleDescriptor, descriptorFile)
    }
}