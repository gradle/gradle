/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph

import org.gradle.api.internal.DomainObjectContext
import spock.lang.Specification

class DependencyGraphPathResolverTest extends Specification {

    /**
     * Reproducer for https://github.com/gradle/gradle/issues/36284.
     *
     * When a direct dependee's upward {@code getDependents()} BFS cannot reach
     * the seeded root, {@code calculatePaths} silently appends {@code null} to
     * its returned collection. That null then flows into
     * {@code ModuleVersionResolveException.withIncomingPaths(...)} and a later
     * {@code getMessage()} call dereferences {@code path.get(0)} → NPE.
     *
     * The fix belongs upstream: this method must never return a collection
     * containing null elements, regardless of graph topology.
     */
    def "does not return null path entries when a direct dependee cannot trace back to the root"() {
        given:
        def owner = Stub(DomainObjectContext) {
            getDisplayName() >> "root project"
        }
        def rootComp = Stub(DependencyGraphComponent)
        def toNode = Stub(DependencyGraphNode) {
            getOwner() >> rootComp
        }
        def directComp = Stub(DependencyGraphComponent) {
            // Empty dependents — the upward BFS from this direct dependee
            // never reaches rootComp, so calculatePaths cannot reconstruct a
            // path. This is the topology that triggers issue 36284.
            getDependents() >> []
        }
        def fromNode = Stub(DependencyGraphNode) {
            getOwner() >> directComp
        }

        when:
        def paths = DependencyGraphPathResolver.calculatePaths([fromNode], toNode, owner)

        then:
        // Every path element must be non-null — regression guard for issue 36284.
        paths.every { it != null }
    }
}
