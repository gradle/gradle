/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dsl.ModuleReplacementsData
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId

class DefaultConflictHandlerTest extends Specification {

    def resolver = Mock(ModuleConflictResolver)
    def replacements = Mock(ModuleReplacementsData)
    @Subject handler = new DefaultConflictHandler(resolver, replacements)
    def details = Mock(ConflictResolverDetails)

    def "registers unconflicted modules"() {
        def a = candidate("org", "a")
        def b = candidate("org", "b")

        expect:
        !handler.registerCandidate(a).conflictExists()
        !handler.registerCandidate(b).conflictExists()
        !handler.hasConflicts()
    }

    def "registers module with version conflict"() {
        def a = candidate("org", "a", "1", "2")
        def b = candidate("org", "b", "1")

        when:
        def aX = handler.registerCandidate(a)
        def bX = handler.registerCandidate(b)

        then:
        aX.conflictExists()
        !bX.conflictExists()
        handler.hasConflicts()
    }

    def "registers module with module conflict"() {
        def a = candidate("org", "a", "1", "2")
        def b = candidate("org", "b", "1")

        replacements.getReplacementFor(DefaultModuleIdentifier.newId("org", "a")) >> new ModuleReplacementsData.Replacement(DefaultModuleIdentifier.newId("org", "b"), null)

        when:
        def aX = handler.registerCandidate(a)
        def bX = handler.registerCandidate(b)

        then:
        aX.conflictExists()
        bX.conflictExists()
        handler.hasConflicts()
    }

    def "resolves conflict"() {
        def a = candidate("org", "a", "1", "2")
        handler.registerCandidate(a)

        when:
        details.getCandidates() >> { a.versions.findAll { it.id.version in ['1', '2']} }
        handler.resolveNextConflict { ConflictResolutionResult r ->
            assert r.selected.id == newId("org", "a", "2")
        }

        then:
        1 * resolver.select(_) >> { args ->
            def details = args[0]
            def selected = details.candidates.find { it.id.version == '2' }
            details.select(selected)
        }
        0 * details._
        0 * resolver._

        then:
        !handler.hasConflicts()
    }

    private CandidateModule candidate(String group, String name, String ... versions) {
        def candidate = Stub(CandidateModule)
        candidate.getId() >> DefaultModuleIdentifier.newId(group, name)
        candidate.getVersions() >> versions.collect { String version ->
            def v = Stub(ComponentState)
            v.getId() >> newId(group, name, version)
            v
        }
        candidate
    }
}
