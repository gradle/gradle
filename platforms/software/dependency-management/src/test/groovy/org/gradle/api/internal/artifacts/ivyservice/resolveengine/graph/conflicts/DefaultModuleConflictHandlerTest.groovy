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

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId

class DefaultModuleConflictHandlerTest extends Specification {

    Map<ModuleIdentifier, ModuleResolveState> allModules = [:]
    ResolveState resolveState = Mock(ResolveState) {
        getModules() >> allModules.values()
        getModule(_ as ModuleIdentifier) >> { ModuleIdentifier id ->
            allModules.computeIfAbsent(id, {
                Mock(ModuleResolveState) {
                    getId() >> id
                }
            })
        }
    }

    def resolver = Mock(ModuleConflictResolver)
    def replacements = Mock(ImmutableModuleReplacements)
    @Subject handler = new DefaultModuleConflictHandler(resolver, replacements, resolveState)

    def "registers unconflicted modules"() {
        def a = candidate("org", "a")
        def b = candidate("org", "b")

        expect:
        !handler.registerCandidate(a)
        !handler.registerCandidate(b)
        !handler.hasConflicts()
    }

    def "registers module with version conflict"() {
        def a = candidate("org", "a", "1", "2")
        def b = candidate("org", "b", "1")

        when:
        def aX = handler.registerCandidate(a)
        def bX = handler.registerCandidate(b)

        then:
        aX
        !bX
        handler.hasConflicts()

        and:
        1 * resolveState.getModule(a.id).clearSelection()
    }

    def "registers module with module conflict"() {
        def a = candidate("org", "a", "1", "2")
        def b = candidate("org", "b", "1")

        replacements.getReplacementFor(DefaultModuleIdentifier.newId("org", "a")) >> new ImmutableModuleReplacements.Replacement(DefaultModuleIdentifier.newId("org", "b"), null)

        when:
        def aX = handler.registerCandidate(a)
        def bX = handler.registerCandidate(b)

        then:
        aX
        bX
        handler.hasConflicts()

        and:
        2 * resolveState.getModule(a.id).clearSelection()
        1 * resolveState.getModule(b.id).clearSelection()
    }

    def "resolves conflict"() {
        given:
        def candidateModule = candidate("org", "a", "1", "2")
        handler.registerCandidate(candidateModule)
        def selectedVersion = candidateModule.versions.find { it.id.version == '2' }
        def targetModule = selectedVersion.module
        resolveState.getModule(DefaultModuleIdentifier.newId("org", "a")) >> targetModule

        when:
        handler.resolveNextConflict()

        then:
        1 * resolver.select(_) >> { args ->
            def details = args[0]
            def selected = details.candidates.find { it.id.version == '2' }
            details.select(selected)
        }
        1 * targetModule.replaceWith(selectedVersion)
        0 * resolver._

        then:
        !handler.hasConflicts()
    }

    private CandidateModule candidate(String group, String name, String ... versions) {
        def moduleId = DefaultModuleIdentifier.newId(group, name)
        return Stub(CandidateModule) {
            getId() >> moduleId
            getVersions() >> versions.collect { String version ->
                Stub(ComponentState) {
                    getId() >> newId(group, name, version)
                    getModule() >> resolveState.getModule(moduleId)
                }
            }
        }
    }

}
