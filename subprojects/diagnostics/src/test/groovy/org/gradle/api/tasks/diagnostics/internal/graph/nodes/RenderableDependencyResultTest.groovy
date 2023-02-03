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

import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector
import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newModule

class RenderableDependencyResultTest extends Specification {

    def "renders name"() {
        given:
        def requested = newSelector(DefaultModuleIdentifier.newId('org.mockito', 'mockito-core'), new DefaultMutableVersionConstraint('1.0'))
        def same = newModule('org.mockito', 'mockito-core', '1.0')
        def differentVersion = newModule('org.mockito', 'mockito-core', '2.0')
        def differentName = newModule('org.mockito', 'mockito', '1.0')
        def differentGroup = newModule('com.mockito', 'mockito', '2.0')

        expect:
        dep(requested, same).name == 'org.mockito:mockito-core:1.0'
        dep(requested, differentVersion).name == 'org.mockito:mockito-core:1.0 -> 2.0'
        dep(requested, differentName).name == 'org.mockito:mockito-core:1.0 -> org.mockito:mockito:1.0'
        dep(requested, differentGroup).name == 'org.mockito:mockito-core:1.0 -> com.mockito:mockito:2.0'
    }

    private RenderableDependencyResult dep(ModuleComponentSelector requested, ResolvedComponentResult selected) {
        ResolvedDependencyResult dependencyResult = Stub() {
            getRequested() >> requested
            getSelected() >> selected
        }
        return new RenderableDependencyResult(dependencyResult)
    }
}
