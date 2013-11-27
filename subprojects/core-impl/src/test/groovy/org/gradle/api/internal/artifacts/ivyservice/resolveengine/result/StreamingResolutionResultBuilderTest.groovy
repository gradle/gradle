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

import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultPrinter.printGraph
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.CONFLICT_RESOLUTION
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons.REQUESTED

class StreamingResolutionResultBuilderTest extends Specification {

    StreamingResolutionResultBuilder builder = new StreamingResolutionResultBuilder(new DummyBinaryStore(), new DummyStore())

    def "result can be read multiple times"() {
        builder.start(newId("org", "root", "1.0"))

        when:
        def result = builder.complete()

        then:
        with(result) {
            root.id == DefaultModuleComponentIdentifier.newId("org", "root", "1.0")
            root.selectionReason == VersionSelectionReasons.ROOT
        }
        printGraph(result.root) == """org:root:1.0
"""
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
        printGraph(result.root) == """org:root:1.0
  org:dep1:2.0(C) [root]
  org:dep2:3.0 -> org:dep2:3.0 - Could not resolve org:dep2:3.0.
"""
    }

    def "visiting resolved module version again has no effect"() {
        builder.start(newId("org", "root", "1.0"))
        builder.resolvedModuleVersion(sel("org", "root", "1.0", REQUESTED)) //it's fine

        builder.resolvedModuleVersion(sel("org", "dep1", "2.0", CONFLICT_RESOLUTION))
        builder.resolvedModuleVersion(sel("org", "dep1", "2.0", REQUESTED)) //will be ignored

        builder.resolvedConfiguration(newId("org", "root", "1.0"),
                [new DefaultInternalDependencyResult(newSelector("org", "dep1", "2.0"), sel("org", "dep1", "2.0", CONFLICT_RESOLUTION), CONFLICT_RESOLUTION, null)])

        when:
        def result = builder.complete()

        then:
        printGraph(result.root) == """org:root:1.0
  org:dep1:2.0(C) [root]
"""
    }

    def "visiting resolved configuration again accumulates dependencies"() {
        builder.start(newId("org", "root", "1.0"))

        builder.resolvedModuleVersion(sel("org", "dep1", "2.0", REQUESTED))
        builder.resolvedModuleVersion(sel("org", "dep2", "2.0", REQUESTED))

        builder.resolvedConfiguration(newId("org", "root", "1.0"), [
                new DefaultInternalDependencyResult(newSelector("org", "dep1", "2.0"), sel("org", "dep1", "2.0", REQUESTED), REQUESTED, null),
        ])
        builder.resolvedConfiguration(newId("org", "root", "1.0"), [
                new DefaultInternalDependencyResult(newSelector("org", "dep2", "2.0"), sel("org", "dep2", "2.0", REQUESTED), REQUESTED, null),
        ])

        when:
        def result = builder.complete()

        then:
        printGraph(result.root) == """org:root:1.0
  org:dep1:2.0 [root]
  org:dep2:2.0 [root]
"""
    }

    def "dependency failures are remembered"() {
        builder.start(newId("org", "root", "1.0"))

        builder.resolvedModuleVersion(sel("org", "dep1", "2.0", REQUESTED))
        builder.resolvedModuleVersion(sel("org", "dep2", "2.0", REQUESTED))

        builder.resolvedConfiguration(newId("org", "root", "1.0"), [
            new DefaultInternalDependencyResult(newSelector("org", "dep1", "2.0"), null, REQUESTED, new ModuleVersionResolveException(newSelector("org", "dep1", "1.0"), new RuntimeException())),
            new DefaultInternalDependencyResult(newSelector("org", "dep2", "2.0"), sel("org", "dep2", "2.0", REQUESTED), REQUESTED, null),
        ])
        builder.resolvedConfiguration(newId("org", "dep2", "2.0"), [
            new DefaultInternalDependencyResult(newSelector("org", "dep1", "5.0"), null, REQUESTED, new ModuleVersionResolveException(newSelector("org", "dep1", "5.0"), new RuntimeException())),
        ])

        when:
        def result = builder.complete()

        then:
        printGraph(result.root) == """org:root:1.0
  org:dep1:2.0 -> org:dep1:1.0 - Could not resolve org:dep1:1.0.
  org:dep2:2.0 [root]
    org:dep1:5.0 -> org:dep1:5.0 - Could not resolve org:dep1:5.0.
"""
    }

    private DefaultModuleVersionSelection sel(String org, String name, String ver, ComponentSelectionReason reason) {
        new DefaultModuleVersionSelection(newId(org, name, ver), reason, new DefaultModuleComponentIdentifier(org, name, ver))
    }
}
