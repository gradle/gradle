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
package org.gradle.api.tasks.diagnostics.internal.graph.nodes

import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class RenderableUnresolvedDependencyResultTest extends Specification {

    def "renders name"() {
        given:
        def requested = newSelector('org.mockito', 'mockito-core', '1.0')
        def same = newSelector('org.mockito', 'mockito-core', '1.0')
        def differentVersion = newSelector('org.mockito', 'mockito-core', '2.0')
        def differentName = newSelector('org.mockito', 'mockito', '1.0')
        def differentGroup = newSelector('com.mockito', 'mockito', '2.0')

        expect:
        dep(requested, same).name == 'org.mockito:mockito-core:1.0'
        dep(requested, differentVersion).name == 'org.mockito:mockito-core:1.0 -> 2.0'
        dep(requested, differentName).name == 'org.mockito:mockito-core:1.0 -> mockito:1.0'
        dep(requested, differentGroup).name == 'org.mockito:mockito-core:1.0 -> com.mockito:mockito:2.0'
    }

    private RenderableUnresolvedDependencyResult dep(ModuleVersionSelector requested, ModuleVersionSelector attempted) {
        UnresolvedDependencyResult dependencyResult = Stub() {
            getRequested() >> requested
            getAttempted() >> attempted
        }
        return new RenderableUnresolvedDependencyResult(dependencyResult)
    }
}
