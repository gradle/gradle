/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.internal.component.external.model.DefaultIvyModuleResolveMetaData
import org.gradle.internal.component.external.model.DefaultMavenModuleResolveMetaData
import spock.lang.Specification
import org.gradle.internal.Factory

class MetadataProviderTest extends Specification {
    Factory metadataSupplier = Mock(Factory)
    MetadataProvider metadataProvider = new MetadataProvider(metadataSupplier)

    def "caches metadata" () {
        when:
        metadataProvider.getMetaData()
        metadataProvider.getMetaData()

        then:
        1 * metadataSupplier.create() >> new DefaultIvyModuleResolveMetaData(Stub(ModuleDescriptor))
    }

    def "can provide component metadata" () {
        when:
        def componentMetadata = metadataProvider.getComponentMetadata()

        then:
        componentMetadata.id.group == "group"
        componentMetadata.id.name == "name"
        componentMetadata.id.version == "1.0"

        and:
        1 * metadataSupplier.create() >> {
            return new DefaultIvyModuleResolveMetaData(Stub(ModuleDescriptor) {
                getModuleRevisionId() >> ModuleRevisionId.newInstance("group", "name", "1.0")
            })
        }
    }

    def "can provide Ivy descriptor" () {
        when:
        def returned = metadataProvider.getIvyModuleDescriptor()

        then:
        returned.ivyStatus == "test"

        and:
        1 * metadataSupplier.create() >> {
            return new DefaultIvyModuleResolveMetaData(Stub(ModuleDescriptor) {
                getStatus() >> "test"
            })
        }
    }

    def "returns null when not Ivy descriptor" () {
        when:
        def returned = metadataProvider.getIvyModuleDescriptor()

        then:
        returned == null

        and:
        1 * metadataSupplier.create() >> {
            return new DefaultMavenModuleResolveMetaData(Stub(ModuleDescriptor), "bundle", false)
        }
    }
}
