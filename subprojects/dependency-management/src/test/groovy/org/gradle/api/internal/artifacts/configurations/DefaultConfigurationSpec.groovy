/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.artifacts.*
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedProjectConfigurationResults
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.tasks.TaskDependency
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.event.ListenerBroadcast
import org.gradle.internal.event.ListenerManager
import spock.lang.Specification

class DefaultConfigurationSpec extends Specification {

    ConfigurationsProvider configurationsProvider = Mock()
    ConfigurationResolver resolver = Mock()
    ListenerManager listenerManager = Mock()
    DependencyMetaDataProvider metaDataProvider = Mock()
    ResolutionStrategyInternal resolutionStrategy = Mock()
    ProjectAccessListener projectAccessListener = Mock()
    ProjectFinder projectFinder = Mock()

    DefaultConfiguration conf(String confName = "conf", String path = ":conf") {
        new DefaultConfiguration(path, confName, configurationsProvider, resolver, listenerManager, metaDataProvider, resolutionStrategy, projectAccessListener, projectFinder)
    }

    DefaultPublishArtifact artifact(String name) {
        artifact(name: name)
    }

    DefaultPublishArtifact artifact(Map props = [:]) {
        new DefaultPublishArtifact(
            props.name ?: "artifact",
            props.extension ?: "artifact",
            props.type,
            props.classifier,
            props.date,
            props.file,
            props.tasks ?: []
        )
    }

    // You need to wrap this in an interaction {} block when calling it
    ResolvedConfiguration resolvedConfiguration(Configuration config, ConfigurationResolver dependencyResolver = resolver) {
        ResolvedConfiguration resolvedConfiguration = Mock()
        def results = new ResolverResults()
        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        results.resolved(resolvedConfiguration, Mock(ResolutionResult), projectConfigurationResults)
        1 * dependencyResolver.resolve(config) >> results
        1 * projectConfigurationResults.allProjectConfigurationResults >> ([] as Set)
        resolvedConfiguration
    }

    def setup() {
        ListenerBroadcast<DependencyResolutionListener> broadcast = new ListenerBroadcast<DependencyResolutionListener>(DependencyResolutionListener)
        _ * listenerManager.createAnonymousBroadcaster(DependencyResolutionListener) >> broadcast
    }

    def "all artifacts collection has immediate artifacts"() {
        given:
        def c = conf()

        when:
        c.artifacts << artifact()
        c.artifacts << artifact()

        then:
        c.allArtifacts.size() == 2
    }

    def "all artifacts collection has inherited artifacts"() {
        given:
        def master = conf()

        def masterParent1 = conf()
        def masterParent2 = conf()
        master.extendsFrom masterParent1, masterParent2

        def masterParent1Parent1 = conf()
        def masterParent1Parent2 = conf()
        masterParent1.extendsFrom masterParent1Parent1, masterParent1Parent2

        def masterParent2Parent1 = conf()
        def masterParent2Parent2 = conf()
        masterParent2.extendsFrom masterParent2Parent1, masterParent2Parent2

        def allArtifacts = master.allArtifacts

        def added = []
        allArtifacts.whenObjectAdded { added << it.name }
        def removed = []
        allArtifacts.whenObjectRemoved { removed << it.name }

        expect:
        allArtifacts.empty

        when:
        masterParent1.artifacts << artifact("p1-1")
        masterParent1Parent1.artifacts << artifact("p1p1-1")
        masterParent1Parent2.artifacts << artifact("p1p2-1")
        masterParent2.artifacts << artifact("p2-1")
        masterParent2Parent1.artifacts << artifact("p2p1-1")
        masterParent2Parent2.artifacts << artifact("p2p2-1")

        then:
        allArtifacts.size() == 6
        added == ["p1-1", "p1p1-1", "p1p2-1", "p2-1", "p2p1-1", "p2p2-1"]

        when:
        masterParent2Parent2.artifacts.remove masterParent2Parent2.artifacts.toList().first()

        then:
        allArtifacts.size() == 5
        removed == ["p2p2-1"]

        when:
        removed.clear()
        masterParent1.extendsFrom = []

        then:
        allArtifacts.size() == 3
        removed == ["p1p1-1", "p1p2-1"]
    }

    def "incoming dependencies set has same name and path as owner configuration"() {
        def config = conf("conf", ":path")

        expect:
        config.incoming.name == "conf"
        config.incoming.path == ":path"
    }

    def "incoming dependencies set contains immediate dependencies"() {
        def config = conf("conf")
        Dependency dep1 = Mock()

        given:
        config.dependencies.add(dep1)

        expect:
        config.incoming.dependencies as List == [dep1]
    }

    def "incoming dependencies set contains inherited dependencies"() {
        def parent = conf("conf")
        def config = conf("conf")
        Dependency dep1 = Mock()

        given:
        config.extendsFrom parent
        parent.dependencies.add(dep1)

        expect:
        config.incoming.dependencies as List == [dep1]
    }

    def "incoming dependencies set files are resolved lazily"() {
        setup:
        def config = conf("conf")

        when:
        def files = config.incoming.files

        then:
        0 * _._

        when:
        files.files

        then:
        interaction { resolvedConfiguration(config) }
        0 * resolver._
    }

    def "incoming dependencies set depends on all self resolving dependencies"() {
        SelfResolvingDependency dependency = Mock()
        Task task = Mock()
        TaskDependency taskDep = Mock()
        def config = conf("conf")
        def resolvedConfiguration = Mock(ResolvedConfiguration)
        def resolverResults = new ResolverResults()
        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        resolverResults.resolved(resolvedConfiguration, Mock(ResolutionResult), projectConfigurationResults)

        given:
        config.dependencies.add(dependency)

        when:
        def depTaskDeps = config.incoming.dependencies.buildDependencies.getDependencies(null)
        def fileTaskDeps = config.incoming.files.buildDependencies.getDependencies(null)

        then:
        depTaskDeps == [task] as Set
        fileTaskDeps == [task] as Set
        _ * resolvedConfiguration.hasError() >> false
        _ * resolver.resolve(config) >> resolverResults
        _ * projectConfigurationResults.allProjectConfigurationResults >> ([] as Set)
        _ * dependency.buildDependencies >> taskDep
        _ * taskDep.getDependencies(_) >> ([task] as Set)
        0 * _._
    }

    def "notifies beforeResolve action on incoming dependencies set when dependencies are resolved"() {
        Action<ResolvableDependencies> action = Mock()
        def config = conf("conf")

        given:
        config.incoming.beforeResolve(action)

        when:
        config.resolvedConfiguration

        then:
        interaction { resolvedConfiguration(config) }
        1 * action.execute(config.incoming)
    }

    def "calls beforeResolve closure on incoming dependencies set when dependencies are resolved"() {
        def config = conf("conf")
        resolvedConfiguration(config)
        def called = false

        expect:
        config.incoming.afterResolve {
            assert it == config.incoming
            called = true
        }

        when:
        config.resolvedConfiguration

        then:
        called
    }

    def "notifies afterResolve action on incoming dependencies set when dependencies are resolved"() {
        Action<ResolvableDependencies> action = Mock()
        def config = conf("conf")

        given:
        config.incoming.afterResolve(action)

        when:
        config.resolvedConfiguration

        then:
        interaction { resolvedConfiguration(config) }
        1 * action.execute(config.incoming)

    }

    def "calls afterResolve closure on incoming dependencies set when dependencies are resolved"() {
        def config = conf("conf")
        resolvedConfiguration(config)
        def called = false

        expect:
        config.incoming.afterResolve {
            assert it == config.incoming
            called = true
        }

        when:
        config.resolvedConfiguration

        then:
        called
    }
    
    def "a recursive copy of a configuration includes inherited exclude rules"() {
        given:
        def (p1, p2, child) = [conf("p1"), conf("p2"), conf("child")]
        child.extendsFrom p1, p2
        
        and:
        def (p1Exclude, p2Exclude) = [[group: 'p1', module: 'p1'], [group: 'p2', module: 'p2']]
        p1.exclude p1Exclude
        p2.exclude p2Exclude
        
        when:
        def copied = child.copyRecursive()
        
        then:
        1 * resolutionStrategy.copy() >> Mock(ResolutionStrategyInternal)
        copied.excludeRules.size() == 2
        copied.excludeRules.collect{[group: it.group, module: it.module]}.sort { it.group } == [p1Exclude, p2Exclude]
    }

    def "copied configuration has own instance of resolution strategy"() {
        def strategy = Mock(ResolutionStrategyInternal)
        def conf = conf()

        when:
        def copy = conf.copy()

        then:
        1 * resolutionStrategy.copy() >> strategy
        conf.resolutionStrategy != copy.resolutionStrategy
        copy.resolutionStrategy == strategy
    }

    def "provides resolution result"() {
        def config = conf("conf")
        def result = Mock(ResolutionResult)
        def resolverResults = new ResolverResults()

        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        resolverResults.resolved(Mock(ResolvedConfiguration), result, projectConfigurationResults)

        when:
        def out = config.incoming.resolutionResult

        then:
        1 * resolver.resolve(config) >> resolverResults
        1 * projectConfigurationResults.allProjectConfigurationResults >> ([] as Set)
        out == result
    }

    def "resolving configuration for task dependencies puts it into the right state"() {
        def config = conf("conf")
        def result = Mock(ResolutionResult)
        def resolverResults = new ResolverResults()
        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        resolverResults.resolved(Mock(ResolvedConfiguration), result, projectConfigurationResults)

        when:
        config.getBuildDependencies()

        then:
        1 * resolver.resolve(config) >> resolverResults
        _ * projectConfigurationResults.getAllProjectConfigurationResults() >> ([] as Set)
        config.internalState == ConfigurationInternal.InternalState.TASK_DEPENDENCIES_RESOLVED
        config.state == Configuration.State.RESOLVED
    }

    def "resolving configuration marks parent configuration as observed"() {
        def parent = conf("parent", ":parent")
        def config = conf("conf")
        config.extendsFrom parent
        def result = Mock(ResolutionResult)
        def resolverResults = new ResolverResults()
        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        resolverResults.resolved(Mock(ResolvedConfiguration), result, projectConfigurationResults)

        when:
        config.resolve()

        then:
        1 * resolver.resolve(config) >> resolverResults
        _ * projectConfigurationResults.getAllProjectConfigurationResults() >> ([] as Set)
        parent.internalState == ConfigurationInternal.InternalState.OBSERVED
    }

    def "resolving configuration puts it into the right state and broadcasts events"() {
        def listenerBroadcaster = Mock(ListenerBroadcast)

        when:
        def config = conf("conf")

        then:
        1 * listenerManager.createAnonymousBroadcaster(_) >> listenerBroadcaster

        def listener = Mock(DependencyResolutionListener)
        def result = Mock(ResolutionResult)
        def resolverResults = new ResolverResults()

        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        resolverResults.resolved(Mock(ResolvedConfiguration), result, projectConfigurationResults)

        when:
        config.incoming.getResolutionResult()

        then:
        1 * listenerBroadcaster.getSource() >> listener
        1 * listener.beforeResolve(config.incoming)
        1 * resolver.resolve(config) >> resolverResults
        1 * projectConfigurationResults.allProjectConfigurationResults >> ([] as Set)
        1 * listener.afterResolve(config.incoming)
        config.internalState == ConfigurationInternal.InternalState.RESULTS_RESOLVED
        config.state == Configuration.State.RESOLVED
    }

    def "resolving configuration for task dependencies, and then resolving it for results does not re-resolve configuration"() {
        def config = conf("conf")
        def result = Mock(ResolutionResult)
        def resolverResults = new ResolverResults()
        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        resolverResults.resolved(Mock(ResolvedConfiguration), result, projectConfigurationResults)

        when:
        config.getBuildDependencies()

        then:
        1 * resolver.resolve(config) >> resolverResults
        2 * projectConfigurationResults.getAllProjectConfigurationResults() >> ([] as Set)
        config.internalState == ConfigurationInternal.InternalState.TASK_DEPENDENCIES_RESOLVED
        config.state == Configuration.State.RESOLVED

        when:
        config.incoming.getResolutionResult()

        then:
        0 * resolver.resolve(_)
        config.internalState == ConfigurationInternal.InternalState.RESULTS_RESOLVED
        config.state == Configuration.State.RESOLVED
    }

    def "resolving configuration for results, and then resolving it for task dependencies does not re-resolve configuration"() {
        def config = conf("conf")
        def result = Mock(ResolutionResult)
        def resolverResults = new ResolverResults()
        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        resolverResults.resolved(Mock(ResolvedConfiguration), result, projectConfigurationResults)

        when:
        config.incoming.getResolutionResult()

        then:
        1 * resolver.resolve(config) >> resolverResults
        1 * projectConfigurationResults.getAllProjectConfigurationResults() >> ([] as Set)
        config.internalState == ConfigurationInternal.InternalState.RESULTS_RESOLVED
        config.state == Configuration.State.RESOLVED

        when:
        config.getBuildDependencies()

        then:
        0 * resolver.resolve(_)
        1 * projectConfigurationResults.getAllProjectConfigurationResults() >> ([] as Set)
        config.internalState == ConfigurationInternal.InternalState.RESULTS_RESOLVED
        config.state == Configuration.State.RESOLVED
    }

    def "resolving configuration twice returns the same result objects"() {
        def config = conf("conf")
        def result = Mock(ResolutionResult)
        def resolverResults = new ResolverResults()
        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        def resolvedConfiguration = Mock(ResolvedConfiguration)
        def resolvedFiles = Mock(Set)
        resolverResults.resolved(resolvedConfiguration, result, projectConfigurationResults)

        when:
        def previousFiles = config.files
        def previousResolutionResult = config.incoming.resolutionResult
        def previousResolvedConfiguration = config.resolvedConfiguration

        then:
        1 * resolver.resolve(config) >> resolverResults
        1 * projectConfigurationResults.getAllProjectConfigurationResults() >> ([] as Set)
        1 * resolvedConfiguration.getFiles(_) >> resolvedFiles
        config.internalState == ConfigurationInternal.InternalState.RESULTS_RESOLVED
        config.state == Configuration.State.RESOLVED

        when:
        def nextFiles = config.files
        def nextResolutionResult = config.incoming.resolutionResult
        def nextResolvedConfiguration = config.resolvedConfiguration

        then:
        0 * resolver.resolve(_)
        1 * resolvedConfiguration.getFiles(_) >> resolvedFiles
        config.internalState == ConfigurationInternal.InternalState.RESULTS_RESOLVED
        config.state == Configuration.State.RESOLVED

        // We get back the same resolution results
        previousResolutionResult == result
        nextResolutionResult == result

        // We get back the same resolved configuration
        previousResolvedConfiguration == resolvedConfiguration
        nextResolvedConfiguration == resolvedConfiguration

        // And the same files
        previousFiles == resolvedFiles
        nextFiles == resolvedFiles
    }

    def "copied configuration is not resolved"() {
        def config = conf("conf")
        def result = Mock(ResolutionResult)
        def resolverResults = new ResolverResults()

        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        resolverResults.resolved(Mock(ResolvedConfiguration), result, projectConfigurationResults)

        when:
        config.incoming.resolutionResult

        then:
        1 * resolver.resolve(config) >> resolverResults
        1 * projectConfigurationResults.getAllProjectConfigurationResults() >> ([] as Set)

        when:
        def copy = config.copy()

        then:
        1 * resolutionStrategy.copy() >> Mock(ResolutionStrategyInternal)
        copy.internalState == ConfigurationInternal.InternalState.UNOBSERVED
        copy.state == Configuration.State.UNRESOLVED
    }

    def "provides task dependency from project dependency using 'needed'"() {
        def conf = conf("conf")
        when: def dep = conf.getTaskDependencyFromProjectDependency(true, "foo") as TasksFromProjectDependencies
        then: dep.taskName == "foo"
    }

    def "provides task dependency from project dependency using 'dependents'"() {
        def conf = conf("conf")
        when: def dep = conf.getTaskDependencyFromProjectDependency(false, "bar") as TasksFromDependentProjects
        then:
        dep.taskName == "bar"
        dep.configurationName == "conf"
    }

    def "mutations are prohibited after resolution"() {
        def conf = conf("conf")
        def result = Mock(ResolutionResult)
        def resolverResults = new ResolverResults()

        def projectConfigurationResults = Mock(ResolvedProjectConfigurationResults)
        resolverResults.resolved(Mock(ResolvedConfiguration), result, projectConfigurationResults)

        when:
        conf.incoming.getResolutionResult()
        then:
        1 * resolver.resolve(conf) >> resolverResults
        1 * projectConfigurationResults.allProjectConfigurationResults >> ([] as Set)

        when: conf.dependencies.add(Mock(Dependency))
        then:
        def exDependency = thrown(InvalidUserDataException);
        exDependency.message == "Cannot change configuration ':conf' after it has been resolved."

        when: conf.artifacts.add(Mock(PublishArtifact))
        then:
        def exArtifact = thrown(InvalidUserDataException);
        exArtifact.message == "Cannot change configuration ':conf' after it has been resolved."
    }

    def "whenEmpty action does not trigger when config has dependencies"() {
        def conf = conf("conf")
        def whenEmptyAction = Mock(Action)
        conf.whenEmpty whenEmptyAction
        conf.dependencies.add(Mock(Dependency))

        when:
        conf.triggerWhenEmptyActionsIfNecessary()

        then:
        0 * _
    }

    def "second whenEmpty action does not trigger if first one already added dependencies"() {
        def conf = conf("conf")
        def whenEmptyAction1 = Mock(Action)
        def whenEmptyAction2 = Mock(Action)
        conf.whenEmpty whenEmptyAction1
        conf.whenEmpty whenEmptyAction2

        when:
        conf.triggerWhenEmptyActionsIfNecessary()

        then:
        1 * whenEmptyAction1.execute(conf.dependencies) >> {
            conf.dependencies.add(Mock(Dependency))
        }
        0 * _
    }

    def "whenEmpty action is called even if parent config has dependencies"() {
        def parent = conf("parent", ":parent")
        parent.dependencies.add(Mock(Dependency))

        def conf = conf("conf")
        def whenEmptyAction = Mock(Action)
        conf.extendsFrom parent
        conf.whenEmpty whenEmptyAction

        when:
        conf.triggerWhenEmptyActionsIfNecessary()

        then:
        1 * whenEmptyAction.execute(conf.dependencies)
        0 * _
    }

    def "whenEmpty action is called on self first, then on parent"() {
        def parentWhenEmptyAction = Mock(Action)
        def parent = conf("parent", ":parent")
        parent.whenEmpty parentWhenEmptyAction

        def conf = conf("conf")
        def whenEmptyAction = Mock(Action)
        conf.extendsFrom parent
        conf.whenEmpty whenEmptyAction

        when:
        conf.triggerWhenEmptyActionsIfNecessary()

        then:
        1 * whenEmptyAction.execute(conf.dependencies)

        then:
        1 * parentWhenEmptyAction.execute(parent.dependencies)
        0 * _
    }
}
