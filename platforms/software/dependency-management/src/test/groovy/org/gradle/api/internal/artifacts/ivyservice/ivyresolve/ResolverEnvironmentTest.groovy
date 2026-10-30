/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.dsl.ImmutableComponentMetadataRules
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultCachePolicy
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.NoOpDerivationStrategy
import org.gradle.internal.rules.RuleAction
import org.gradle.internal.rules.SpecRuleAction
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class ResolverEnvironmentTest extends Specification {

    def selectionRule = new SpecRuleAction<>(Stub(RuleAction), Specs.satisfyAll())

    def "environments built from separate snapshots of the same state are equal"() {
        given:
        def repository = Stub(ResolutionAwareRepository)

        when:
        def first = environment([repository])
        def second = environment([repository])

        then:
        first == second
        first.hashCode() == second.hashCode()
    }

    def "repositories are compared by identity"() {
        when:
        def first = environment([Stub(ResolutionAwareRepository)])
        def second = environment([Stub(ResolutionAwareRepository)])

        then:
        first != second
    }

    def "selection rules are snapshotted at construction"() {
        given:
        def liveRules = []
        def selectionRules = Stub(ComponentSelectionRulesInternal) {
            getRules() >> { liveRules }
        }
        def environment = environment([], selectionRules)

        when:
        liveRules.add(selectionRule)

        then:
        environment.componentSelectionRules().isEmpty()
        environment.getComponentSelectionRules().rules.isEmpty()
    }

    def "selection rules are compared by content"() {
        when:
        def first = environment([], rules(selectionRule))
        def second = environment([], rules(selectionRule))

        then:
        first == second
        first.hashCode() == second.hashCode()

        when:
        def different = environment([], rules(new SpecRuleAction<>(Stub(RuleAction), Specs.satisfyAll())))

        then:
        first != different
    }

    def "cache expiration controls are compared by value"() {
        expect:
        new DefaultCachePolicy().asImmutable() == new DefaultCachePolicy().asImmutable()
        new DefaultCachePolicy().asImmutable().hashCode() == new DefaultCachePolicy().asImmutable().hashCode()

        and:
        def policy = new DefaultCachePolicy()
        policy.cacheChangingModulesFor(10, TimeUnit.SECONDS)
        new DefaultCachePolicy().asImmutable() != policy.asImmutable()
    }

    private ComponentSelectionRulesInternal rules(SpecRuleAction<? super ComponentSelection>... actions) {
        Stub(ComponentSelectionRulesInternal) {
            getRules() >> actions.toList()
        }
    }

    private ResolverEnvironment environment(List<ResolutionAwareRepository> repositories) {
        return environment(repositories, Stub(ComponentSelectionRulesInternal));
    }

    private ResolverEnvironment environment(List<ResolutionAwareRepository> repositories, ComponentSelectionRulesInternal selectionRules) {
        new ResolverEnvironment(
            repositories,
            ImmutableComponentMetadataRules.EMPTY,
            NoOpDerivationStrategy.instance,
            selectionRules,
            false,
            new DefaultCachePolicy().asImmutable(),
            ImmutableAttributesSchema.EMPTY
        )
    }
}
