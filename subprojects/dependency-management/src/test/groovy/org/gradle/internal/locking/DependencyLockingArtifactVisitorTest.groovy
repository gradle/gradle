/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.locking

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.internal.component.local.model.RootConfigurationMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import spock.lang.Specification
import spock.lang.Subject

import static java.util.Collections.emptySet
import static java.util.Collections.singleton
import static org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId

class DependencyLockingArtifactVisitorTest extends Specification {

    String configuration = 'config'
    DependencyLockingProvider dependencyLockingProvider = Mock()
    RootGraphNode rootNode = Mock()
    RootConfigurationMetadata metadata = Mock()
    DependencyLockingState lockState = Mock()
    ModuleIdentifier mid = DefaultModuleIdentifier.newId("org", "foo")

    @Subject
    def visitor = new DependencyLockingArtifactVisitor(configuration, dependencyLockingProvider)

    def 'initialises when there is lock state'() {
        when:
        visitor.startArtifacts(rootNode)

        then:
        1 * rootNode.metadata >> metadata
        1 * metadata.dependencyLockingState >> lockState
        1 * lockState.mustValidateLockState() >> true
        1 * lockState.lockedDependencies >> Collections.emptySet()
        0 * _
    }

    def 'initialises when there is no lock state'() {
        when:
        visitor.startArtifacts(rootNode)

        then:
        1 * rootNode.metadata >> metadata
        1 * metadata.dependencyLockingState >> lockState
        1 * lockState.mustValidateLockState() >> false
        0 * _
    }

    def 'processes node having a ModuleComponentIdentifier'() {
        given:
        startWithState([])

        DependencyGraphNode node = Mock()
        DependencyGraphComponent component = Mock()

        when:
        visitor.visitNode(node)

        then:
        2 * node.owner >> component
        1 * component.componentId >> newId(mid, '1.0')
    }

    def 'ignores node having a ModuleComponentIdentifier but an empty version'() {
        given:
        startWithState([])

        DependencyGraphNode node = Mock()
        DependencyGraphComponent component = Mock()
        ModuleComponentIdentifier identifier = Mock()
        ComponentResolveMetadata metadata = Mock()

        when:
        visitor.visitNode(node)

        then:
        2 * node.owner >> component
        1 * component.componentId >> identifier
        1 * identifier.version >> ''
        1 * component.metadata >> metadata
        1 * metadata.isChanging() >> false
        0 * _
    }

    def 'ignores node not having a ModuleComponentIdentifier'() {
        given:
        startWithState([])

        DependencyGraphNode node = Mock()
        DependencyGraphComponent component = Mock()
        ComponentIdentifier identifier = Mock()


        when:
        visitor.visitNode(node)

        then:
        2 * node.owner >> component
        1 * component.componentId >> identifier
        1 * component.metadata >> null
        0 * _
    }

    def 'finishes without error when visited match expected'() {
        given:
        def id = newId(mid, '1.1')
        startWithState([id])
        addVisitedNode(id)

        when:
        visitor.finishArtifacts()

        then:
        notThrown(LockOutOfDateException)
    }

    def 'throws when extra modules visited'() {
        given:
        startWithState([])
        addVisitedNode(newId(mid, '1.0'))

        when:
        visitor.complete()

        then:
        def ex = thrown(LockOutOfDateException)
        ex.message.contains("Resolved 'org:foo:1.0' which is not part of the lock state")
    }

    def 'throws when module not visited'() {
        given:
        startWithState([newId(mid, '1.1')])

        when:
        visitor.complete()

        then:
        def ex = thrown(LockOutOfDateException)
        ex.message.contains("Did not resolve 'org:foo:1.1' which is part of the lock state")
    }

    def 'invokes locking provider on complete with visited modules'() {
        given:
        def identifier = newId(mid, '1.1')
        startWithoutLockState()
        addVisitedNode(identifier)

        when:
        visitor.complete()

        then:
        1 * dependencyLockingProvider.persistResolvedDependencies(configuration, singleton(identifier), emptySet())

    }

    def 'invokes locking provider on complete with visited modules and indicates changing modules seen'() {
        given:
        def identifier = newId(mid, '1.1')
        startWithoutLockState()
        addVisitedChangingNode(identifier)

        when:
        visitor.complete()

        then:
        1 * dependencyLockingProvider.persistResolvedDependencies(configuration, singleton(identifier), singleton(identifier))

    }


    private void addVisitedNode(ModuleComponentIdentifier module) {
        DependencyGraphNode node = Mock()
        DependencyGraphComponent component = Mock()
        node.owner >> component
        component.componentId >> module

        visitor.visitNode(node)
    }

    private void addVisitedChangingNode(ModuleComponentIdentifier module) {
        DependencyGraphNode node = Mock()
        DependencyGraphComponent component = Mock()
        ComponentResolveMetadata metadata = Mock()
        node.owner >> component
        component.metadata >> metadata
        metadata.isChanging() >> true
        component.componentId >> module

        visitor.visitNode(node)
    }

    private startWithoutLockState() {
        rootNode.metadata >> metadata
        metadata.dependencyLockingState >> lockState
        lockState.mustValidateLockState() >> false

        visitor.startArtifacts(rootNode)
    }

    private startWithState(List<ModuleComponentIdentifier> locks) {
        rootNode.metadata >> metadata
        metadata.dependencyLockingState >> lockState
        lockState.mustValidateLockState() >> true
        lockState.lockedDependencies >> locks

        visitor.startArtifacts(rootNode)
    }
}
