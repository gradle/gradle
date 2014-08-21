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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleRevisionResolveState
import spock.lang.Specification
import spock.lang.Subject

import static com.google.common.collect.Sets.newHashSet
import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId

class DefaultConflictHandlerTest extends Specification {

    def resolver = Mock(ModuleConflictResolver)
    def replacements = Mock(ModuleReplacementsData)
    @Subject handler = new DefaultConflictHandler(resolver, replacements)

    def "registers unconflicted modules"() {
        def a = candidate("org", "a")
        def b = candidate("org", "b")

        expect:
        !handler.registerModule(a)
        !handler.registerModule(b)
        !handler.hasConflicts()
        handler.conflictCount == 0
    }

    def "registers module with version conflict"() {
        def a = candidate("org", "a", "1", "2")
        def b = candidate("org", "b", "1")

        when:
        def aX = handler.registerModule(a)
        def bX = handler.registerModule(b)

        then:
        aX
        !bX
        handler.hasConflicts()
        handler.conflictCount == 1
    }

    def "registers known module with new versions"() {
        def a1 = candidate("org", "a", "1")
        def a2 = candidate("org", "a", "1", "2")
        def a3 = candidate("org", "a", "1", "2", "3")

        when:
        def a1X = handler.registerModule(a1)
        def a2X = handler.registerModule(a2)
        def a3X = handler.registerModule(a3)

        then:
        !a1X
        handler.conflictCount == 1
    }

    def "resolves conflict"() {
        def x = candidate("org", "x", "5")
        def a1 = candidate("org", "a", "1")
        def a2 = candidate("org", "a", "1", "2")
        def a3 = candidate("org", "a", "1", "2", "3")

        handler.registerModule(x)
        handler.registerModule(a1)
        handler.registerModule(a2)
        handler.registerModule(a3)

        when:
        handler.resolveNextConflict { ConflictResolutionResult r ->
            assert r.selected.id == newId("org", "a", "3")
        }

        then:
        1 * resolver.select({ newHashSet(it*.id.version) == newHashSet("1", "2", "3") }) >> { args -> args[0].find { it.id.version == "3" } }
        0 * resolver._

        then:
        handler.conflictCount == 0
    }

    //TODO SF I don't like those tests. There are getting hard to write. Let's keep this coverage until DefaultConflictHandler is refactored

    def "module conflict when replacement is first"() {
        def a = candidate("org", "a", "1", "2")
        def b = candidate("org", "b", "3", "4")

        replacements.getReplacementFor(DefaultModuleIdentifier.newId("org", "a")) >> DefaultModuleIdentifier.newId("org", "b")

        handler.registerModule(b)
        handler.registerModule(a)

        when:
        handler.resolveNextConflict { ConflictResolutionResult r ->
            assert r.selected.id == newId("org", "b", "4")
        }

        then:
        1 * resolver.select({ it*.id.version == ["3", "4"] }) >> { args -> args[0].find { it.id.version == "4" } }
        0 * resolver._

        then:
        handler.conflictCount == 0
    }

    def "module conflict when replacement is last"() {
        def a = candidate("org", "a", "1", "2")
        def b = candidate("org", "b", "3", "4")

        replacements.getReplacementFor(DefaultModuleIdentifier.newId("org", "a")) >> DefaultModuleIdentifier.newId("org", "b")

        handler.registerModule(a)
        handler.registerModule(b)

        when:
        handler.resolveNextConflict { ConflictResolutionResult r ->
            assert r.selected.id == newId("org", "b", "4")
        }

        then:
        1 * resolver.select({ it*.id.version == ["3", "4"] }) >> { args -> args[0].find { it.id.version == "4" } }
        0 * resolver._

        then:
        handler.conflictCount == 0
    }

    private CandidateModule candidate(String group, String name, String ... versions) {
        def candidate = Stub(CandidateModule)
        candidate.getId() >> DefaultModuleIdentifier.newId(group, name)
        candidate.getVersions() >> versions.collect { String version ->
            def v = Stub(ModuleRevisionResolveState)
            v.getId() >> newId(group, name, version)
            v
        }
        candidate
    }

    def "handles replacement chain"() {
        def a = candidate("org", "a", "1")
        def b = candidate("org", "b", "2")
        def c = candidate("org", "c", "3")

        replacements.getReplacementFor(DefaultModuleIdentifier.newId("org", "a")) >> DefaultModuleIdentifier.newId("org", "b")
        replacements.getReplacementFor(DefaultModuleIdentifier.newId("org", "b")) >> DefaultModuleIdentifier.newId("org", "c")

        handler.registerModule(a)
        handler.registerModule(b)
        handler.registerModule(c)

        when:
        handler.conflictCount == 1
        handler.resolveNextConflict { ConflictResolutionResult r ->
            assert r.selected.id == newId("org", "c", "3")
        }

        then:
        1 * resolver.select({ it*.id.version == ["3"] }) >> { args -> args[0].find { it.id.version == "3" } }
        0 * resolver._
    }
}
