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
import org.gradle.api.internal.artifacts.dsl.dependencies.LockEntryFilter
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import spock.lang.Specification
import spock.lang.Subject

import static java.util.Collections.emptySet
import static java.util.Collections.singleton
import static org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId

class DependencyLockingGraphVisitorTest extends Specification {

    String configuration = 'config'
    DependencyLockingProvider dependencyLockingProvider = Mock()
    RootGraphNode rootNode = Mock()
    DependencyLockingState lockState = Mock()
    ModuleIdentifier mid = DefaultModuleIdentifier.newId("org", "foo")

    @Subject
    def visitor = new DependencyLockingGraphVisitor(configuration, dependencyLockingProvider)

    def 'initialises when there is lock state'() {
        when:
        visitor.start(rootNode)

        then:
        1 * dependencyLockingProvider.loadLockState(configuration) >> lockState
        1 * lockState.mustValidateLockState() >> true
        1 * lockState.lockedDependencies >> emptySet()
        0 * _
    }

    def 'initialises when there is no lock state'() {
        when:
        visitor.start(rootNode)

        then:
        1 * dependencyLockingProvider.loadLockState(configuration) >> lockState
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
        ComponentGraphResolveMetadata metadata = Mock()

        when:
        visitor.visitNode(node)

        then:
        2 * node.owner >> component
        1 * node.root >> false
        1 * component.componentId >> identifier
        1 * identifier.version >> ''
        1 * component.metadataOrNull >> metadata
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
        1 * node.root >> false
        1 * component.componentId >> identifier
        1 * component.metadataOrNull >> null
        0 * _
    }

    def 'ignores root node'() {
        given:
        startWithState([])

        DependencyGraphComponent component = Mock()
        ComponentIdentifier identifier = Mock()

        when:
        visitor.visitNode(rootNode)

        then:
        2 * rootNode.owner >> component
        1 * rootNode.root >> true
        1 * component.componentId >> identifier
        1 * component.metadataOrNull >> null

        and:
        visitor.allResolvedModules.empty
    }

    def 'finishes without error when visited match expected'() {
        given:
        def id = newId(mid, '1.1')
        startWithState([id])
        addVisitedNode(id)

        when:
        visitor.finish(rootNode)

        then:
        notThrown(LockOutOfDateException)
    }

    def 'generates failures when extra modules visited'() {
        given:
        startWithState([])
        addVisitedNode(newId(mid, '1.0'))

        when:
        def failures = visitor.collectLockingFailures()

        then:
        failures.size() == 1
        failures.each {
            assert it.problem instanceof LockOutOfDateException
            assert it.problem.message.contains("Resolved 'org:foo:1.0' which is not part of the dependency lock state")
        }
    }

    def 'generates failures when module not visited'() {
        given:
        startWithState([newId(mid, '1.1')])

        when:
        def failures = visitor.collectLockingFailures()

        then:
        failures.size() == 1
        failures.each {
            assert it.problem instanceof LockOutOfDateException
            assert it.problem.message.contains("Did not resolve 'org:foo:1.1' which is part of the dependency lock state")
        }
    }

    def 'generates failures when module visited with different version'() {
        given:
        startWithState([newId(mid, '1.1')])
        addVisitedNode(newId(mid, '1.0'))

        when:
        def failures = visitor.collectLockingFailures()

        then:
        failures.size() == 1
        failures.each {
            assert it.problem instanceof LockOutOfDateException
            assert it.problem.message.contains("Did not resolve 'org:foo:1.1' which has been forced / substituted to a different version: '1.0'")
        }
    }

    def 'invokes locking provider on writeLocks with visited modules'() {
        given:
        def identifier = newId(mid, '1.1')
        startWithoutLockState()
        addVisitedNode(identifier)

        when:
        visitor.writeLocks()

        then:
        1 * dependencyLockingProvider.persistResolvedDependencies(configuration, singleton(identifier), emptySet())

    }

    def 'invokes locking provider on writeLocks with visited modules and indicates changing modules seen'() {
        given:
        def identifier = newId(mid, '1.1')
        startWithoutLockState()
        addVisitedChangingNode(identifier)

        when:
        visitor.writeLocks()

        then:
        1 * dependencyLockingProvider.persistResolvedDependencies(configuration, singleton(identifier), singleton(identifier))

    }

    def 'ignores visited node that is to be ignored'() {
        given:
        def identifier = newId(mid, '1.1')
        def ignoredIdentifier = newId(DefaultModuleIdentifier.newId('org', 'ignored'), '1.0')
        startWithState([identifier], LockEntryFilterFactory.forParameter(['org:ignored'], "Update lock", true))
        addVisitedNode(identifier)
        addVisitedNode(ignoredIdentifier)

        when:
        def failures = visitor.collectLockingFailures()

        then:
        failures.isEmpty()
    }


    private void addVisitedNode(ModuleComponentIdentifier module) {
        DependencyGraphNode node = Mock()
        DependencyGraphComponent owner = Mock()
        ComponentResolutionState component = Mock()
        node.owner >> owner
        node.component >> component
        owner.componentId >> module

        visitor.visitNode(node)
    }

    private void addVisitedChangingNode(ModuleComponentIdentifier module) {
        DependencyGraphNode node = Mock()
        DependencyGraphComponent component = Mock()
        ComponentGraphResolveMetadata metadata = Mock()
        node.owner >> component
        component.metadataOrNull >> metadata
        metadata.isChanging() >> true
        component.componentId >> module

        visitor.visitNode(node)
    }

    private startWithoutLockState() {
        dependencyLockingProvider.loadLockState(configuration) >> lockState
        lockState.mustValidateLockState() >> false

        visitor.start(rootNode)
    }

    private startWithState(List<ModuleComponentIdentifier> locks, LockEntryFilter ignoredEntries = LockEntryFilterFactory.FILTERS_NONE) {
        dependencyLockingProvider.loadLockState(configuration) >> lockState
        lockState.mustValidateLockState() >> true
        lockState.lockedDependencies >> locks
        lockState.ignoredEntryFilter >> ignoredEntries

        visitor.start(rootNode)
    }
}
