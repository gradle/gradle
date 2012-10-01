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

package org.gradle.api.internal.artifacts.result

import org.gradle.api.artifacts.result.ModuleVersionSelectionReason
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

/**
 * by Szczepan Faber, created at: 9/20/12
 */
class DefaultResolutionResultTest extends Specification {

    def "provides all dependencies"() {
        given:
        def root = newModule('root')
        def dep1 = newDependency('dep1')
        def dep2 = newDependency('dep2')
        root.addDependency(dep1)
        root.addDependency(dep2)

        def dep3 = newDependency('dep3')
        dep2.selected.addDependency(dep3)

        when:
        def all = new DefaultResolutionResult(root).allDependencies

        then:
        all.size() == 3
        all.containsAll(dep1, dep2, dep3)
    }

    def "deals with dependency cycles"() {
        given:
        // a->b->a
        def root = newModule('a', 'a', '1')
        def dep1 = newDependency('b', 'b', '1')
        root.addDependency(dep1)
        dep1.selected.addDependency(new DefaultResolvedDependencyResult(newSelector('a', 'a', '1'), root, dep1.selected))

        when:
        def all = new DefaultResolutionResult(root).allDependencies

        then:
        all.size() == 2
    }

    def newDependency(String group='a', String module='a', String version='1') {
        new DefaultResolvedDependencyResult(newSelector(group, module, version), newModule(group, module, version), newModule())
    }

    def newModule(String group='a', String module='a', String version='1', ModuleVersionSelectionReason selectionReason = VersionSelectionReasons.REQUESTED) {
        new DefaultResolvedModuleVersionResult(newId(group, module, version), selectionReason)
    }
}
