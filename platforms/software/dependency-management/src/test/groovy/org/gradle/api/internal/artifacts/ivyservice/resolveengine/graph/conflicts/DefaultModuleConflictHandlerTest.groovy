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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleResolveState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveState
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Subject

class DefaultModuleConflictHandlerTest extends Specification {

    Map<ModuleIdentifier, ModuleResolveState> allModules = [:]
    Map<ModuleIdentifier, ComponentState> selections = [:]
    ResolveState resolveState = Mock(ResolveState) {
        getModules() >> allModules.values()
        getModule(_ as ModuleIdentifier) >> { ModuleIdentifier id ->
            allModules.computeIfAbsent(id, {
                Mock(ModuleResolveState) {
                    getId() >> id
                    getSelected() >> { selections[id] }
                }
            })
        }
    }

    def replacements = Mock(ImmutableModuleReplacements)
    @Subject handler = new DefaultModuleConflictHandler(replacements, resolveState)

    def "registers unconflicted modules"() {
        def a = candidate("org", "a")
        def b = candidate("org", "b")

        expect:
        !handler.registerCandidate(a)
        !handler.registerCandidate(b)
        !handler.hasConflicts()
    }

    def "registers module with module conflict"() {
        given:
        registerReplacement("a", "b")
        def a = candidate("org", "a", "1")
        def b = candidate("org", "b", "1")

        when:
        def aX = handler.registerCandidate(a)
        def bX = handler.registerCandidate(b)

        then:
        !aX
        bX
        handler.hasConflicts()

        and:
        1 * resolveState.getModule(a.id).markInModuleConflict()
        1 * resolveState.getModule(b.id).markInModuleConflict()
    }

    def "resolves conflict with the current selection of the replacing module"() {
        given:
        registerReplacement("a", "b")
        def a = candidate("org", "a", "1")
        def b = candidate("org", "b", "1")
        handler.registerCandidate(a)
        handler.registerCandidate(b)
        def selected = b.versions[0]
        selections[b.id] = selected

        when:
        handler.resolveNextConflict()

        then:
        1 * resolveState.getModule(b.id).resolveModuleConflict(selected)

        then:
        1 * resolveState.getModule(a.id).resolveModuleConflict(selected)

        and:
        !handler.hasConflicts()
    }

    @Issue("https://github.com/gradle/gradle/issues/39922")
    def "resolves conflict with a selection made after the replacing module was registered"() {
        given:
        registerReplacement("a", "b")
        def a = candidate("org", "a", "1")
        def b = candidate("org", "b", "1")
        handler.registerCandidate(b)
        handler.registerCandidate(a)
        def reselected = Stub(ComponentState)
        selections[b.id] = reselected

        when:
        handler.resolveNextConflict()

        then:
        1 * resolveState.getModule(b.id).resolveModuleConflict(reselected)

        then:
        1 * resolveState.getModule(a.id).resolveModuleConflict(reselected)
    }

    private void registerReplacement(String a, String b) {
        replacements.getReplacementFor(DefaultModuleIdentifier.newId("org", a)) >> new ImmutableModuleReplacements.Replacement(DefaultModuleIdentifier.newId("org", b), null)
    }

    private CandidateModule candidate(String group, String name, String ... versions) {
        def moduleId = DefaultModuleIdentifier.newId(group, name)
        ModuleResolveState module = resolveState.getModule(moduleId)
        return Stub(CandidateModule) {
            getId() >> moduleId
            getVersions() >> versions.collect { String version ->
                Stub(ComponentState) {
                    getComponentId() >> DefaultModuleComponentIdentifier.newId(moduleId, version)
                    getModule() >> module
                }
            }
        }
    }

}
