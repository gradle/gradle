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

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import org.gradle.api.Action
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.ComponentSelectionRules
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules
import org.gradle.api.internal.artifacts.configurations.ConflictResolution
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.internal.Actions
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.rules.NoInputsRuleAction
import org.gradle.util.TestUtil
import org.gradle.vcs.internal.VcsResolver
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY

class DefaultResolutionStrategySpec extends Specification {

    def cachePolicy = new DefaultCachePolicy()
    def dependencySubstitutions = Mock(DependencySubstitutionsInternal)
    def globalDependencySubstitutions = Mock(GlobalDependencyResolutionRules) {
        getDependencySubstitutionRules() >> Stub(DependencySubstitutionRules)
    }
    def componentSelectorConverter = Mock(ComponentSelectorConverter)
    def vcsResolver = Mock(VcsResolver)

    @Shared
    def dependencyLockingProvider = Mock(DependencyLockingProvider)

    def moduleIdentifierFactory = new DefaultImmutableModuleIdentifierFactory()
    def strategy = new DefaultResolutionStrategy(cachePolicy, dependencySubstitutions, globalDependencySubstitutions, vcsResolver, moduleIdentifierFactory, componentSelectorConverter, dependencyLockingProvider, null, TestUtil.objectFactory())

    def "allows setting forced modules"() {
        expect:
        strategy.forcedModules.empty

        when:
        strategy.force 'org.foo:bar:1.0', 'org.foo:baz:2.0'

        then:
        def versions = strategy.forcedModules as List
        versions.size() == 2

        versions[0].group == 'org.foo'
        versions[0].name == 'bar'
        versions[0].version == '1.0'

        versions[1].group == 'org.foo'
        versions[1].name == 'baz'
        versions[1].version == '2.0'
    }

    def "allows replacing forced modules"() {
        given:
        strategy.force 'org.foo:bar:1.0'

        when:
        strategy.forcedModules = ['hello:world:1.0', [group:'g', name:'n', version:'1']]

        then:
        def versions = strategy.forcedModules as List
        versions.size() == 2
        versions[0].group == 'hello'
        versions[1].group == 'g'
    }

    def "provides dependency resolve rule that forces modules"() {
        def mid = DefaultModuleIdentifier.newId('org', 'foo')
        given:
        strategy.force 'org:bar:1.0', 'org:foo:2.0'
        def details = Mock(DependencySubstitutionInternal)

        when:
        strategy.dependencySubstitutionRule.execute(details)

        then:
        _ * dependencySubstitutions.ruleAction >> Actions.doNothing()
        _ * globalDependencySubstitutions.ruleAction >> Actions.doNothing()
        _ * details.getRequested() >> DefaultModuleComponentSelector.newSelector(mid, new DefaultMutableVersionConstraint("1.0"))
        _ * details.getOldRequested() >> newSelector(mid, "1.0")
        1 * details.useTarget(DefaultModuleComponentSelector.newSelector(mid, "2.0"), ComponentSelectionReasons.FORCED)
        0 * details._
    }

    def "eachDependency calls through to substitution rules"() {
        given:
        def action = Mock(Action)

        when:
        strategy.eachDependency(action)

        then:
        1 * dependencySubstitutions.allWithDependencyResolveDetails(action, componentSelectorConverter)
    }

    def "provides dependency resolve rule with forced modules first and then user specified rules"() {
        def mid = DefaultModuleIdentifier.newId('org', 'foo')
        given:
        strategy.force 'org:bar:1.0', 'org:foo:2.0'
        def details = Mock(DependencySubstitutionInternal)
        def substitutionAction = Mock(Action)

        when:
        strategy.dependencySubstitutionRule.execute(details)

        then: //forced modules:
        dependencySubstitutions.ruleAction >> substitutionAction
        _ * details.requested >> DefaultModuleComponentSelector.newSelector(mid, new DefaultMutableVersionConstraint("1.0"))
        _ * details.oldRequested >> newSelector(mid, "1.0")
        1 * details.useTarget(DefaultModuleComponentSelector.newSelector(mid, "2.0"), ComponentSelectionReasons.FORCED)
        _ * globalDependencySubstitutions.ruleAction >> Actions.doNothing()

        then: //user rules follow:
        1 * substitutionAction.execute(details)
        0 * details._
    }

    def "copied instance does not share state"() {
        when:
        def copy = strategy.copy()

        then:
        !copy.is(strategy)
        !copy.cachePolicy.is(strategy.cachePolicy)
        !copy.componentSelection.is(strategy.componentSelection)
    }

    def "use global substitution rules state is not share with copy"() {
        when:
        def copy = strategy.copy()
        strategy.useGlobalDependencySubstitutionRules.set(false)

        then:
        !strategy.useGlobalDependencySubstitutionRules.get()
        copy.useGlobalDependencySubstitutionRules.get()
    }

    def "provides a copy"() {
        given:
        def newDependencySubstitutions = Mock(DependencySubstitutionsInternal)
        dependencySubstitutions.copy() >> newDependencySubstitutions

        strategy.failOnVersionConflict()
        strategy.failOnDynamicVersions()
        strategy.failOnChangingVersions()
        strategy.force("org:foo:1.0")
        strategy.componentSelection.addRule(new NoInputsRuleAction<ComponentSelection>({}))

        when:
        def copy = strategy.copy()

        then:
        copy.forcedModules == strategy.forcedModules
        copy.componentSelection.rules == strategy.componentSelection.rules
        copy.conflictResolution == ConflictResolution.strict

        strategy.cachePolicy == cachePolicy
        copy.cachePolicy != cachePolicy

        strategy.dependencySubstitution == dependencySubstitutions
        copy.dependencySubstitution == newDependencySubstitutions

        ((ResolutionStrategyInternal)copy).isFailingOnDynamicVersions() == ((ResolutionStrategyInternal)strategy).isFailingOnDynamicVersions()
        ((ResolutionStrategyInternal)copy).isFailingOnChangingVersions() == ((ResolutionStrategyInternal)strategy).isFailingOnChangingVersions()
    }

    def "configures changing modules cache with jdk5+ units"() {
        given:
        def id = DefaultModuleVersionIdentifier.newId("foo", "bar", "1.0")
        ResolvedModuleVersion moduleVersion = () -> id

        when:
        strategy.cacheChangingModulesFor(30000, "milliseconds")

        then:
        !strategy.getCachePolicy().asImmutable().changingModuleExpiry(DefaultModuleComponentIdentifier.newId(id), moduleVersion, Duration.ofMillis(30000)).isMustCheck()
        strategy.getCachePolicy().asImmutable().changingModuleExpiry(DefaultModuleComponentIdentifier.newId(id), moduleVersion, Duration.ofMillis(30001)).isMustCheck()
    }

    def "configures changing modules cache with jdk6+ units"() {
        given:
        def id = DefaultModuleVersionIdentifier.newId("foo", "bar", "1.0")
        ResolvedModuleVersion moduleVersion = () -> id

        when:
        strategy.cacheChangingModulesFor(5, "minutes")

        then:
        !strategy.getCachePolicy().asImmutable().changingModuleExpiry(DefaultModuleComponentIdentifier.newId(id), moduleVersion, Duration.ofMinutes(5)).isMustCheck()
        strategy.getCachePolicy().asImmutable().changingModuleExpiry(DefaultModuleComponentIdentifier.newId(id), moduleVersion, Duration.ofMinutes(6)).isMustCheck()
    }

    def "configures dynamic version cache with jdk5+ units"() {
        given:
        def moduleId = DefaultModuleIdentifier.newId("foo", "bar")
        def id = DefaultModuleVersionIdentifier.newId("foo", "bar", "1.0")

        when:
        strategy.cacheDynamicVersionsFor(10000, "milliseconds")

        then:
        !strategy.getCachePolicy().asImmutable().versionListExpiry(moduleId, [id] as Set, Duration.ofMillis(10000)).isMustCheck()
        strategy.getCachePolicy().asImmutable().versionListExpiry(moduleId, [id] as Set, Duration.ofMillis(10001)).isMustCheck()
    }

    def "configures dynamic version cache with jdk6+ units"() {
        given:
        def moduleId = DefaultModuleIdentifier.newId("foo", "bar")
        def id = DefaultModuleVersionIdentifier.newId("foo", "bar", "1.0")

        when:
        strategy.cacheDynamicVersionsFor(1, "hours")

        then:
        !strategy.getCachePolicy().asImmutable().versionListExpiry(moduleId, [id] as Set, Duration.ofHours(1)).isMustCheck()
        strategy.getCachePolicy().asImmutable().versionListExpiry(moduleId, [id] as Set, Duration.ofHours(2)).isMustCheck()
    }

    def "mutation is checked for public API"() {
        def validator = Mock(MutationValidator)
        strategy.setMutationValidator(validator)

        when: strategy.failOnVersionConflict()
        then: 1 * validator.validateMutation(STRATEGY)

        when: strategy.failOnDynamicVersions()
        then: 1 * validator.validateMutation(STRATEGY)

        when: strategy.failOnChangingVersions()
        then: 1 * validator.validateMutation(STRATEGY)

        when: strategy.failOnNonReproducibleResolution()
        then: 2 * validator.validateMutation(STRATEGY)

        when: strategy.force("org.utils:api:1.3")
        then: 1 * validator.validateMutation(STRATEGY)

        when: strategy.forcedModules = ["org.utils:api:1.4"]
        then: (1.._) * validator.validateMutation(STRATEGY)

        // DependencySubstitutionsInternal.allWithDependencyResolveDetails() will call back to validateMutation() instead
        when: strategy.eachDependency(Actions.doNothing())
        then: 1 * validator.validateMutation(STRATEGY)

        when: strategy.componentSelection.all(Actions.doNothing())
        then: 1 * validator.validateMutation(STRATEGY)

        when: strategy.componentSelection(new Action<ComponentSelectionRules>() {
            @Override
            void execute(ComponentSelectionRules componentSelectionRules) {
                componentSelectionRules.all(Actions.doNothing())
            }
        })
        then: 1 * validator.validateMutation(STRATEGY)
    }

    def "mutation is not checked for copy"() {
        given:
        dependencySubstitutions.copy() >> Mock(DependencySubstitutionsInternal)
        def validator = Mock(MutationValidator)
        strategy.setMutationValidator(validator)
        def copy = strategy.copy()

        when: copy.failOnVersionConflict()
        then: 0 * validator.validateMutation(_)

        when: copy.force("org.utils:api:1.3")
        then: 0 * validator.validateMutation(_)

        when: copy.forcedModules = ["org.utils:api:1.4"]
        then: 0 * validator.validateMutation(_)

        when: copy.componentSelection.all(Actions.doNothing())
        then: 0 * validator.validateMutation(_)

        when: copy.componentSelection(new Action<ComponentSelectionRules>() {
            @Override
            void execute(ComponentSelectionRules componentSelectionRules) {
                componentSelectionRules.all(Actions.doNothing())
            }
        })
        then: 0 * validator.validateMutation(_)
    }

    def 'provides the expected DependencyLockingProvider'() {
        when:
        strategy.activateDependencyLocking()

        then:
        strategy.dependencyLockingProvider.is(dependencyLockingProvider)
    }

    def "copies dependency verification state"() {
        when:
        strategy.disableDependencyVerification()

        then:
        !strategy.dependencyVerificationEnabled
        !strategy.copy().dependencyVerificationEnabled

        when:
        strategy.enableDependencyVerification()

        then:
        strategy.dependencyVerificationEnabled
        strategy.copy().dependencyVerificationEnabled
    }
}
