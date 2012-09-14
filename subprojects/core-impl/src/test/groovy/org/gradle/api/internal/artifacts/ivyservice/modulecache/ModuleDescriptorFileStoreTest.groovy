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

import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository
import org.gradle.api.internal.filestore.PathKeyFileStore
import spock.lang.Specification
import org.gradle.api.internal.filestore.FileStoreEntry
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.Action

class ModuleDescriptorFileStoreTest extends Specification {
    ModuleDescriptorFileStore store
    PathKeyFileStore pathKeyFileStore = Mock()
    ModuleRevisionId moduleRevisionId = Mock()
    ModuleVersionRepository repository = Mock()
    FileStoreEntry fileStoreEntry = Mock()
    ModuleDescriptor moduleDescriptor = Mock()

    def setup() {
        store = new ModuleDescriptorFileStore(pathKeyFileStore);
        _ * repository.getId() >> "repositoryId"
        _ * moduleRevisionId.getOrganisation() >> "org.test"
        _ * moduleRevisionId.getName() >> "testArtifact"
        _ * moduleRevisionId.getRevision() >> "1.0"
        _ * moduleDescriptor.getModuleRevisionId() >> moduleRevisionId
    }

    def "getModuleDescriptorFile uses PathKeyFileStore to get file"() {
        when:
        store.getModuleDescriptorFile(repository, moduleRevisionId);

        then:
        1 * pathKeyFileStore.get("module-metadata/org.test/testArtifact/1.0/repositoryId.ivy.xml") >> fileStoreEntry
    }

    def "writeModuleDescriptorFile uses PathKeyFileStore to write file"() {
        when:
        store.writeModuleDescriptorFile(repository, moduleDescriptor);

        then:
        1 * pathKeyFileStore.add("module-metadata/org.test/testArtifact/1.0/repositoryId.ivy.xml", {f -> _} as Action<File>) >> fileStoreEntry
    }
}