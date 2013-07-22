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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class AbstractRenderableDependencyResultSpec extends Specification {

    def "renders name cleanly"() {
        given:
        def requested = newSelector('org.mockito', 'mockito-core', '1.0')

        expect:
        dep(requested, newId('org.mockito', 'mockito-core', '1.0')).name == 'org.mockito:mockito-core:1.0'
        dep(requested, newId('org.mockito', 'mockito-core', '2.0')).name == 'org.mockito:mockito-core:1.0 -> 2.0'
        dep(requested, newId('org.mockito', 'mockito', '1.0')).name == 'org.mockito:mockito-core:1.0 -> org.mockito:mockito:1.0'
        dep(requested, newId('com.mockito', 'mockito', '2.0')).name == 'org.mockito:mockito-core:1.0 -> com.mockito:mockito:2.0'
    }

    private AbstractRenderableDependencyResult dep(ModuleVersionSelector requested, ModuleVersionIdentifier selected) {
        return new AbstractRenderableDependencyResult() {
            ModuleVersionSelector getRequested() {
                return requested
            }

            ModuleVersionIdentifier getActual() {
                return selected
            }

            boolean isResolvable() {
                throw new UnsupportedOperationException()
            }

            Set<RenderableDependency> getChildren() {
                throw new UnsupportedOperationException()
            }
        }
    }
}
