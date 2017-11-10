/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.model.LocalComponentDependencyMetadata
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult
import spock.lang.Specification

class RepositoryChainDependencyToComponentIdResolverTest extends Specification {

    def "resolves with failure if no version is specified"() {
        given:
        def resolver = new RepositoryChainDependencyToComponentIdResolver(null, null, null, null)
        def requested = DefaultModuleVersionSelector.newSelector("group", "a", "")
        def componentSelector = DefaultModuleComponentSelector.newSelector(requested)
        def dependency = new LocalComponentDependencyMetadata(componentSelector, requested , "from", null, "to", [] as Set, [], false, false, true)
        def result = new DefaultBuildableComponentIdResolveResult()

        when:
        resolver.resolve(dependency, null, result)

        then:
        result.hasResult()
        result.failure
        result.failure.message == 'No version specified for group:a'
    }
}
