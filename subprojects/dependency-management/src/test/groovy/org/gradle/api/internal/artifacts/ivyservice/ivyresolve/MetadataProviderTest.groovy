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
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.DefaultIvyModuleResolveMetaData
import org.gradle.internal.component.external.model.DefaultMavenModuleResolveMetaData
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import spock.lang.Specification

class MetadataProviderTest extends Specification {
    Factory metadataSupplier = Mock(Factory)
    MetadataProvider metadataProvider = new MetadataProvider(metadataSupplier)

    def "caches metadata supplier result"() {
        when:
        metadataProvider.getMetaData()
        metadataProvider.getMetaData()

        then:
        1 * metadataSupplier.create() >> new DefaultBuildableModuleComponentMetaDataResolveResult()
    }

    def "verifies that metadata was provided"() {
        given:
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
        result.resolved(new DefaultIvyModuleResolveMetaData(Stub(ModuleDescriptor)))

        when:
        boolean metaData = metadataProvider.resolve()

        then:
        1 * metadataSupplier.create() >> result
        metaData
    }

    def "verifies that metadata was not provided"() {
        given:
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()

        when:
        boolean metaData = metadataProvider.getMetaData()

        then:
        1 * metadataSupplier.create() >> result
        !metaData
    }

    def "can provide component metadata" () {
        given:
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
        def metaData = new DefaultIvyModuleResolveMetaData(Stub(ModuleDescriptor) {
            getModuleRevisionId() >> ModuleRevisionId.newInstance("group", "name", "1.0")
        })
        result.resolved(metaData)

        when:
        def componentMetadata = metadataProvider.getComponentMetadata()

        then:
        componentMetadata.id.group == "group"
        componentMetadata.id.name == "name"
        componentMetadata.id.version == "1.0"

        and:
        1 * metadataSupplier.create() >> result
    }

    def "can provide Ivy descriptor" () {
        given:
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
        def metaData = new DefaultIvyModuleResolveMetaData(Stub(ModuleDescriptor) {
            getStatus() >> "test"
        })
        result.resolved(metaData)

        when:
        def returned = metadataProvider.getIvyModuleDescriptor()

        then:
        returned.ivyStatus == "test"

        and:
        1 * metadataSupplier.create() >> result
    }

    def "returns null when not Ivy descriptor" () {
        given:
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
        def metaData = new DefaultMavenModuleResolveMetaData(Stub(ModuleDescriptor), "bundle", false)
        result.resolved(metaData)

        when:
        def returned = metadataProvider.getIvyModuleDescriptor()

        then:
        returned == null

        and:
        1 * metadataSupplier.create() >> result
    }
}
