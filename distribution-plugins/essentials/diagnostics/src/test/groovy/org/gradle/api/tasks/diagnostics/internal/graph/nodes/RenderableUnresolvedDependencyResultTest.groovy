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

import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

class RenderableUnresolvedDependencyResultTest extends Specification {
    static VersionConstraint v(String version) {
        new DefaultMutableVersionConstraint(version)
    }

    def "renders name"() {
        given:
        def requested = newSelector(DefaultModuleIdentifier.newId('org.mockito', 'mockito-core'), v('1.0'))
        def same = newSelector(DefaultModuleIdentifier.newId('org.mockito', 'mockito-core'), v('1.0'))
        def differentVersion = newSelector(DefaultModuleIdentifier.newId('org.mockito', 'mockito-core'), v('2.0'))
        def differentName = newSelector(DefaultModuleIdentifier.newId('org.mockito', 'mockito'), v('1.0'))
        def differentGroup = newSelector(DefaultModuleIdentifier.newId('com.mockito', 'mockito'), v('2.0'))

        expect:
        dep(requested, same).name == 'org.mockito:mockito-core:1.0'
        dep(requested, differentVersion).name == 'org.mockito:mockito-core:1.0 -> 2.0'
        dep(requested, differentName).name == 'org.mockito:mockito-core:1.0 -> org.mockito:mockito:1.0'
        dep(requested, differentGroup).name == 'org.mockito:mockito-core:1.0 -> com.mockito:mockito:2.0'
    }

    private RenderableUnresolvedDependencyResult dep(ModuleComponentSelector requested, ModuleComponentSelector attempted) {
        UnresolvedDependencyResult dependencyResult = Stub() {
            getRequested() >> requested
            getAttempted() >> attempted
        }
        return new RenderableUnresolvedDependencyResult(dependencyResult)
    }
}
