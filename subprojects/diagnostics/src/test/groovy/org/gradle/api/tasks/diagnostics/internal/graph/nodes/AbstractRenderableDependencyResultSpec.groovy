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

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.TestComponentIdentifiers
import spock.lang.Specification

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class AbstractRenderableDependencyResultSpec extends Specification {

    def "renders name for ModuleComponentSelector"() {
        given:
        def requested = DefaultModuleComponentSelector.newSelector('org.mockito', 'mockito-core', '1.0')

        expect:
        dep(requested, DefaultModuleComponentIdentifier.newId('org.mockito', 'mockito-core', '1.0')).name == 'org.mockito:mockito-core:1.0'
        dep(requested, DefaultModuleComponentIdentifier.newId('org.mockito', 'mockito-core', '2.0')).name == 'org.mockito:mockito-core:1.0 -> 2.0'
        dep(requested, DefaultModuleComponentIdentifier.newId('org.mockito', 'mockito', '1.0')).name == 'org.mockito:mockito-core:1.0 -> org.mockito:mockito:1.0'
        dep(requested, DefaultModuleComponentIdentifier.newId('com.mockito', 'mockito', '2.0')).name == 'org.mockito:mockito-core:1.0 -> com.mockito:mockito:2.0'
        dep(requested, DefaultModuleComponentIdentifier.newId('com.mockito.other', 'mockito-core', '3.0')).name == 'org.mockito:mockito-core:1.0 -> com.mockito.other:mockito-core:3.0'
        dep(requested, DefaultModuleComponentIdentifier.newId('com.mockito.other', 'mockito-core', '1.0')).name == 'org.mockito:mockito-core:1.0 -> com.mockito.other:mockito-core:1.0'
        dep(requested, newProjectId(':a')).name == 'org.mockito:mockito-core:1.0 -> project :a'
    }

    def "renders name for ProjectComponentSelector"() {
        given:
        def requested = TestComponentIdentifiers.newSelector(':a')

        expect:
        dep(requested, newProjectId(':a')).name == 'project :a'
        dep(requested, newProjectId(':b')).name == 'project :a -> project :b'
        dep(requested, DefaultModuleComponentIdentifier.newId('org.somegroup', 'module', '1.0')).name == 'project :a -> org.somegroup:module:1.0'
    }

    private AbstractRenderableDependencyResult dep(ComponentSelector requested, ComponentIdentifier selected) {
        return new AbstractRenderableDependencyResult() {
            ComponentSelector getRequested() {
                return requested
            }

            ComponentIdentifier getActual() {
                return selected
            }

            RenderableDependency.ResolutionState getResolutionState() {
                throw new UnsupportedOperationException()
            }

            Set<RenderableDependency> getChildren() {
                throw new UnsupportedOperationException()
            }
        }
    }
}
