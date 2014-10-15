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

import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentResolver
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import spock.lang.Specification

class RepositoryChainAdapterTest extends Specification {
    def metaDataResolver = Mock(DependencyToComponentResolver)
    def dynamicVersionResolver = Mock(DependencyToComponentIdResolver)
    def idResult = Mock(BuildableComponentIdResolveResult)
    def versionSelectorScheme = Stub(VersionSelectorScheme)
    def requested = new DefaultModuleVersionSelector("group", "module", "version")
    def id = new DefaultModuleComponentIdentifier("group", "module", "version")
    def mvId = new DefaultModuleVersionIdentifier("group", "module", "version")
    def dependency = Stub(DependencyMetaData) {
        getRequested() >> requested
    }
    def resolver = new RepositoryChainAdapter(dynamicVersionResolver, metaDataResolver, versionSelectorScheme)

    def "short-circuits static version resolution"() {
        given:
        versionSelectorScheme.parseSelector("version") >> {
            Stub(VersionSelector) {
                isDynamic() >> false
            }
        }

        when:
        resolver.resolve(dependency, idResult)

        then:
        1 * idResult.resolved(id, mvId)
    }

    def "resolves dynamic version"() {
        given:
        versionSelectorScheme.parseSelector("version") >> {
            Stub(VersionSelector) {
                isDynamic() >> true
            }
        }

        when:
        resolver.resolve(dependency, idResult)

        then:
        1 * dynamicVersionResolver.resolve(dependency, idResult)
    }
}
