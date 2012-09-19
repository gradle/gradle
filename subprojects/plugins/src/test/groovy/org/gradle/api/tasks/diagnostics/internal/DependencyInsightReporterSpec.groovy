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

package org.gradle.api.tasks.diagnostics.internal

import org.gradle.api.artifacts.result.ModuleVersionSelectionReason
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedModuleVersionResult
import spock.lang.Specification

import static org.gradle.api.artifacts.result.ModuleVersionSelectionReason.conflictResolution
import static org.gradle.api.artifacts.result.ModuleVersionSelectionReason.forced
import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import org.gradle.api.tasks.diagnostics.internal.DependencyInsightReporter

/**
 * Created: 23/08/2012
 * @author Szczepan Faber
 */
class DependencyInsightReporterSpec extends Specification {

    def "sorts dependencies"() {
        def dependencies = [dep("a", "x", "1.0", "2.0"), dep("a", "x", "1.5", "2.0"), dep("b", "a", "5.0"), dep("a", "z", "1.0"), dep("a", "x", "2.0")]

        when:
        def sorted = new DependencyInsightReporter().prepare(dependencies);

        then:
        sorted.size() == 5

        sorted[0].name == 'a:x:2.0'
        !sorted[0].description

        sorted[1].name == 'a:x:1.0 -> 2.0'
        !sorted[1].description

        sorted[2].name == 'a:x:1.5 -> 2.0'
        !sorted[2].description

        sorted[3].name == 'a:z:1.0'
        !sorted[3].description

        sorted[4].name == 'b:a:5.0'
        !sorted[4].description
    }

    def "adds header dependency if the selected version does not exist in the graph"() {
        def dependencies = [dep("a", "x", "1.0", "2.0", forced), dep("a", "x", "1.5", "2.0", forced), dep("b", "a", "5.0")]

        when:
        def sorted = new DependencyInsightReporter().prepare(dependencies);

        then:
        sorted.size() == 4

        sorted[0].name == 'a:x:2.0'
        sorted[0].description == 'forced'

        sorted[1].name == 'a:x:1.0 -> 2.0'
        !sorted[1].description

        sorted[2].name == 'a:x:1.5 -> 2.0'
        !sorted[2].description

        sorted[3].name == 'b:a:5.0'
        !sorted[3].description
    }

    def "annotates only first dependency in the group"() {
        def dependencies = [dep("a", "x", "1.0", "2.0", conflictResolution), dep("a", "x", "2.0", "2.0", conflictResolution), dep("b", "a", "5.0", "5.0", forced)]

        when:
        def sorted = new DependencyInsightReporter().prepare(dependencies);

        then:
        sorted.size() == 3

        sorted[0].name == 'a:x:2.0'
        sorted[0].description == 'conflict resolution'

        sorted[1].name == 'a:x:1.0 -> 2.0'
        !sorted[1].description

        sorted[2].name == 'b:a:5.0'
        sorted[2].description == 'forced'
    }

    private dep(String group, String name, String requested, String selected = requested, ModuleVersionSelectionReason selectionReason = ModuleVersionSelectionReason.requested) {
        def selectedModule = new DefaultResolvedModuleVersionResult(newId(group, name, selected), selectionReason)
        new DefaultResolvedDependencyResult(newSelector(group, name, requested),
                selectedModule,
                new DefaultResolvedModuleVersionResult(newId("a", "root", "1")))
    }
}
