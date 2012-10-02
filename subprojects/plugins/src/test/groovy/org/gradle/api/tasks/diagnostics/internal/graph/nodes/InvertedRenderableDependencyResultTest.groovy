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

import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedModuleVersionResult
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newModule

/**
 * by Szczepan Faber, created at: 9/21/12
 */
class InvertedRenderableDependencyResultTest extends Specification {

    def "uses dependents as children"() {
        expect:
        /*
        //TODO SF at some point, find a better way to construct test data (graphs)
        root->y->b(1.0)
        root->x->b(1.0)
        root->z->b(0.5)
        */

        def root = newModule("root")

        def x = newDependency("org", "x", "1.0", "1.0", root)
        def y = newDependency("org", "y", "1.0", "1.0", root)
        def z = newDependency("org", "z", "1.0", "1.0", root)

        def selectedB = newModule("org", "b", "1.0")

        def b1 = newDependency("org", "b", "1.0", "1.0", x.selected, selectedB)
        def b2 = newDependency("org", "b", "1.0", "1.0", y.selected, selectedB)
        def b3 = newDependency("org", "b", "1.0", "0.5", z.selected, selectedB)

        when:
        def result = new InvertedRenderableDependencyResult(b3, null)

        then:
        result.children*.name == ['org:z:1.0']

        when:
        result = new InvertedRenderableDependencyResult(b2, null)

        then:
        result.children*.name == ['org:x:1.0', 'org:y:1.0']
    }

    static DefaultResolvedDependencyResult newDependency(String group='a', String module='a', String version='1', String requested = version,
                                                         DefaultResolvedModuleVersionResult from = newModule(),
                                                         DefaultResolvedModuleVersionResult selected = newModule(group, module, version)) {
        def out = new DefaultResolvedDependencyResult(newSelector(group, module, requested), selected, from)
        selected.addDependent(out)
        out
    }
}
