/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.result.ModuleVersionSelectionReason
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.CONFLICT_RESOLUTION

class StreamingResolutionResultBuilderTest extends Specification {

    StreamingResolutionResultBuilder builder = new StreamingResolutionResultBuilder()

    def "maintains single result in byte stream"() {
        builder.start(newId("org", "root", "1.0"))

        when:
        def result = builder.complete()

        then:
        with(result) {
            root.id == newId("org", "root", "1.0")
            root.selectionReason == VersionSelectionReasons.ROOT
            root.dependencies.empty
            root.dependents.empty
            allModuleVersions == [root] as Set
            allDependencies.empty
        }
    }

    def "maintains graph in byte stream"() {
        builder.start(newId("org", "root", "1.0"))

        builder.resolvedModuleVersion(sel("org", "dep1", "2.0", CONFLICT_RESOLUTION))
        builder.resolvedConfiguration(newId("org", "root", "1.0"), [
                new DefaultInternalDependencyResult(newSelector("org", "dep1", "2.0"), sel("org", "dep1", "2.0", CONFLICT_RESOLUTION), CONFLICT_RESOLUTION, null),
                new DefaultInternalDependencyResult(newSelector("org", "dep2", "3.0"), null, CONFLICT_RESOLUTION, new ModuleVersionResolveException(newSelector("org", "dep2", "3.0"), new RuntimeException("Boo!")))
        ])

        when:
        def result = builder.complete()

        then:
        with(result) {
            root.selectionReason == VersionSelectionReasons.ROOT
            root.dependencies*.toString() == ['org:dep1:2.0', 'org:dep2:3.0 -> org:dep2:3.0 - Could not resolve org:dep2:3.0.']
            root.dependents.empty
            allModuleVersions*.toString() == ['org:root:1.0', 'org:dep1:2.0']
        }
    }

    def "visiting resolved module version again has no effect"() {

    }

    def "visiting resolved configuration again accumulates dependencies"() {

    }

    def "dependency failures are remembered"() {

    }

    def "dependency graph is recursive"() {
        //instances are reused accross the graph
    }

    private DefaultModuleVersionSelection sel(String org, String name, String ver, ModuleVersionSelectionReason reason) {
        new DefaultModuleVersionSelection(newId(org, name, ver), reason)
    }
}
