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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultIvyModuleResolveMetaData
import org.gradle.internal.component.external.model.DefaultMavenModuleResolveMetaData
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import spock.lang.Specification

class MetadataProviderTest extends Specification {
    def repo = Mock(ModuleComponentRepositoryAccess)
    def dep = Stub(DependencyMetaData)
    def id = Stub(ModuleComponentIdentifier) {
        getVersion() >> "1.2"
    }
    def metaData = Stub(MutableModuleComponentResolveMetaData)
    def metadataProvider = new MetadataProvider(dep, id, repo)

    def "caches metadata result"() {
        when:
        metadataProvider.getMetaData()
        metadataProvider.getMetaData()

        then:
        1 * repo.resolveComponentMetaData(_, id, _) >> { dependency, compId, result ->
            result.resolved(metaData)
        }
        0 * repo._
    }

    def "verifies that metadata was provided"() {
        given:
        repo.resolveComponentMetaData(_, id, _) >> { dependency, compId, result ->
            result.resolved(metaData)
        }

        expect:
        metadataProvider.resolve()
        metadataProvider.usable
        metadataProvider.metaData
    }

    def "verifies that metadata was not provided"() {
        given:
        repo.resolveComponentMetaData(_, id, _) >> { dependency, compId, result ->
            result.missing()
        }

        expect:
        !metadataProvider.resolve()
        !metadataProvider.usable
    }

    def "can provide component metadata" () {
        given:
        repo.resolveComponentMetaData(_, id, _) >> { dependency, compId, result ->
            result.resolved(metaData)
        }

        when:
        def componentMetadata = metadataProvider.getComponentMetadata()

        then:
        componentMetadata.metadata == metaData
    }

    def "can provide Ivy descriptor" () {
        given:
        def metaData = new DefaultIvyModuleResolveMetaData(Stub(ModuleDescriptor) {
            getStatus() >> "test"
        })
        repo.resolveComponentMetaData(_, id, _) >> { dependency, compId, result ->
            result.resolved(metaData)
        }

        when:
        def returned = metadataProvider.getIvyModuleDescriptor()

        then:
        returned.ivyStatus == "test"
    }

    def "returns null when not Ivy descriptor" () {
        given:
        repo.resolveComponentMetaData(_, id, _) >> { dependency, compId, result ->
            result.resolved(metaData)
        }

        expect:
        metadataProvider.getIvyModuleDescriptor() == null
    }
}
