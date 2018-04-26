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

import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyConstraint
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.internal.component.local.model.RootConfigurationMetadata
import spock.lang.Specification
import spock.lang.Subject

import static java.util.Collections.singleton

class DependencyLockingArtifactVisitorTest extends Specification {

    String configuration = 'config'
    DependencyLockingProvider dependencyLockingProvider = Mock()
    RootGraphNode rootNode = Mock()
    RootConfigurationMetadata metadata = Mock()
    DependencyLockingState lockState = Mock()
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

    def 'process node having a ModuleComponentIdentifier'() {
        given:
        startWithState([])

        DependencyGraphNode node = Mock()
        DependencyGraphComponent component = Mock()
        ModuleComponentIdentifier identifier = Mock()

        when:
        visitor.visitNode(node)

        then:
        1 * node.owner >> component
        1 * component.componentId >> identifier
        1 * identifier.displayName >> 'org:foo:1.0'

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
        1 * node.owner >> component
        1 * component.componentId >> identifier
        0 * _
    }

    def 'finishes without error when visited match expected'() {
        given:
        startWithState([DefaultDependencyConstraint.strictConstraint('org', 'foo', '1.1')])
        addVisitedNode('org:foo:1.1')

        when:
        visitor.finishArtifacts()

        then:
        notThrown(LockOutOfDateException)
    }

    def 'throws when extra modules visited'() {
        given:
        startWithState([])
        addVisitedNode('org:foo:1.0')

        when:
        visitor.finishArtifacts()

        then:
        def ex = thrown(LockOutOfDateException)
        ex.message.contains("Resolved 'org:foo:1.0' which is not part of the lock state")
    }

    def 'throws when module not visited'() {
        given:
        startWithState([DefaultDependencyConstraint.strictConstraint('org', 'foo', '1.1')])

        when:
        visitor.finishArtifacts()

        then:
        def ex = thrown(LockOutOfDateException)
        ex.message.contains("Did not resolve 'org:foo:1.1' which is part of the lock state")
    }

    def 'invokes locking provider on complete with visited modules'() {
        given:
        startWithoutLockState()
        def identifier = addVisitedNode('org:foo:1.0')

        when:
        visitor.complete()

        then:
        1 * dependencyLockingProvider.persistResolvedDependencies(configuration, singleton(identifier))

    }

    private ModuleComponentIdentifier addVisitedNode(String module) {
        DependencyGraphNode node = Mock()
        DependencyGraphComponent component = Mock()
        ModuleComponentIdentifier identifier = Mock()
        node.owner >> component
        component.componentId >> identifier
        identifier.displayName >> module

        visitor.visitNode(node)

        return identifier

    }

    private startWithoutLockState() {
        rootNode.metadata >> metadata
        metadata.dependencyLockingState >> lockState
        lockState.mustValidateLockState() >> false

        visitor.startArtifacts(rootNode)
    }

    private startWithState(List<DependencyConstraint> constraints) {
        rootNode.metadata >> metadata
        metadata.dependencyLockingState >> lockState
        lockState.mustValidateLockState() >> true
        lockState.lockedDependencies >> constraints

        visitor.startArtifacts(rootNode)
    }
}
