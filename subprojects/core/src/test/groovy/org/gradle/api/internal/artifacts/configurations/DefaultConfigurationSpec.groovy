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
import org.gradle.api.Task
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.tasks.TaskDependency
import org.gradle.listener.ListenerBroadcast
import org.gradle.listener.ListenerManager
import spock.lang.Specification
import org.gradle.api.artifacts.*

class DefaultConfigurationSpec extends Specification {

    ConfigurationsProvider configurationsProvider = Mock()
    ArtifactDependencyResolver dependencyResolver = Mock()
    ListenerManager listenerManager = Mock()
    DependencyMetaDataProvider metaDataProvider = Mock()
    DefaultResolutionStrategy defaultResolutionStrategy = Mock()

    DefaultConfiguration conf(String confName = "conf", String path = ":conf") {
        new DefaultConfiguration(path, confName, configurationsProvider, dependencyResolver, listenerManager, metaDataProvider, defaultResolutionStrategy)
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
    ResolvedConfiguration resolvedConfiguration(Configuration config, ArtifactDependencyResolver dependencyResolver = dependencyResolver) {
        ResolvedConfiguration resolvedConfiguration = Mock()
        1 * dependencyResolver.resolve(config) >> new ResolverResults(resolvedConfiguration, Mock(ResolutionResult))
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
        0 * dependencyResolver._
    }

    def "incoming dependencies set depends on all self resolving dependencies"() {
        SelfResolvingDependency dependency = Mock()
        Task task = Mock()
        TaskDependency taskDep = Mock()
        def config = conf("conf")

        given:
        config.dependencies.add(dependency)

        when:
        def depTaskDeps = config.incoming.dependencies.buildDependencies.getDependencies(null)
        def fileTaskDeps = config.incoming.files.buildDependencies.getDependencies(null)

        then:
        depTaskDeps == [task] as Set
        fileTaskDeps == [task] as Set
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
        copied.excludeRules.size() == 2
        copied.excludeRules.collect{[group: it.group, module: it.module]}.sort { it.group } == [p1Exclude, p2Exclude]
    }
}