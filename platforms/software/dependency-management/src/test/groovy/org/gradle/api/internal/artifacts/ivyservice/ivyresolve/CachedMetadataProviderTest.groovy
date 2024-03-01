/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import spock.lang.Specification
import spock.lang.Subject

class CachedMetadataProviderTest extends Specification {

    def cachedResult = Stub(BuildableModuleComponentMetaDataResolveResult)
    def componentState = Stub(ModuleComponentGraphResolveState)
    @Subject
    CachedMetadataProvider provider

    def 'verifies that metadata was provided when state is Resolved'() {
        given:
        cachedResult.state >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
        cachedResult.metaData >> componentState
        provider = new CachedMetadataProvider(cachedResult)

        expect:
        provider.usable
        provider.componentMetadata
    }

    def 'verifies that metadata was not provided when state is #state'() {
        given:
        cachedResult.state >> state
        provider = new CachedMetadataProvider(cachedResult)

        expect:
        !provider.usable
        !provider.componentMetadata

        where:
        state << [BuildableModuleComponentMetaDataResolveResult.State.Unknown, BuildableModuleComponentMetaDataResolveResult.State.Failed, BuildableModuleComponentMetaDataResolveResult.State.Missing]
    }

    def 'returns IvyModuleDescriptor when available'() {
        given:
        cachedResult.state >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
        cachedResult.metaData >> componentState
        componentState.moduleResolveMetadata >> Mock(IvyModuleResolveMetadata)
        provider = new CachedMetadataProvider(cachedResult)

        expect:
        provider.ivyModuleDescriptor
    }

    def 'returns null for IvyModuleDescriptor when not available'() {
        given:
        cachedResult.state >> BuildableModuleComponentMetaDataResolveResult.State.Resolved
        cachedResult.metaData >> componentState
        provider = new CachedMetadataProvider(cachedResult)

        expect:
        !provider.ivyModuleDescriptor
    }
}
