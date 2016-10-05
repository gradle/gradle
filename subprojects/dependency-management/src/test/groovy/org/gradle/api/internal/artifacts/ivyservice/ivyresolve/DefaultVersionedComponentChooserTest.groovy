/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.result.DefaultBuildableComponentSelectionResult
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.rules.ClosureBackedRuleAction
import org.gradle.internal.rules.SpecRuleAction
import spock.lang.Specification

import static org.gradle.internal.resolve.result.BuildableComponentSelectionResult.State.Failed
import static org.gradle.internal.resolve.result.BuildableComponentSelectionResult.State.NoMatch

class DefaultVersionedComponentChooserTest extends Specification {
    def versionSelectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator())
    def versionComparator = new DefaultVersionComparator()
    def componentSelectionRules = Mock(ComponentSelectionRulesInternal)

    def chooser = new DefaultVersionedComponentChooser(versionComparator, versionSelectorScheme, componentSelectionRules)

    def "chooses latest version for component meta data"() {
        def one = Stub(ComponentResolveMetadata) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }
        def two = Stub(ComponentResolveMetadata) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.1")
        }
        def three = Stub(ComponentResolveMetadata) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.2")
        }

        when:
        0 * componentSelectionRules.apply(_,_)

        then:
        chooser.selectNewestComponent(one, two) == two

        when:
        0 * componentSelectionRules.apply(_,_)

        then:
        chooser.selectNewestComponent(two, three) == three
    }

    def "chooses non-generated descriptor over generated"() {
        def one = Mock(ComponentResolveMetadata) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }
        def two = Mock(ComponentResolveMetadata) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }

        when:
        1 * one.generated >> true
        1 * two.generated >> false
        0 * componentSelectionRules.apply(_,_)

        then:
        chooser.selectNewestComponent(one, two) == two

        when:
        1 * one.generated >> false
        0 * componentSelectionRules.apply(_,_)

        then:
        chooser.selectNewestComponent(one, two) == one
    }

    def "chooses newest matching version without requiring metadata"() {
        given:
        def selector = DefaultModuleVersionSelector.of("group", "name", "1.+")
        def selected = DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
        def dependency = Mock(DependencyMetadata)
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = new DefaultBuildableComponentSelectionResult()

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, dependency.getRequested())

        then:
        _ * dependency.requested >> selector
        _ * a.version >> "1.2"
        _ * b.version >> "1.3"
        _ * b.id >> selected
        _ * c.version >> "2.0"
        _ * componentSelectionRules.rules >> []
        0 * _

        and:
        selectedComponentResult.match == selected
    }

    def "chooses newest matching version requiring metadata"() {
        given:
        def selector = DefaultModuleVersionSelector.of("group", "name", "latest.milestone")
        def selected = DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def dependency = Mock(DependencyMetadata)
        def selectedComponentResult = new DefaultBuildableComponentSelectionResult()

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, dependency.getRequested())

        then:
        _ * dependency.requested >> selector
        _ * dependency.withRequestedVersion(_) >> Stub(DependencyMetadata)
        _ * a.version >> "1.2"
        _ * b.version >> "1.3"
        _ * b.id >> selected
        _ * c.version >> "2.0"
        1 * c.resolve() >> resolvedWithStatus("integration")
        1 * b.resolve() >> resolvedWithStatus("milestone")
        _ * componentSelectionRules.rules >> []
        0 * _

        and:
        selectedComponentResult.match == selected
    }

    def "rejects dynamic version by rule without metadata" () {
        given:
        def selector = DefaultModuleVersionSelector.of("group", "name", "1.+")
        def selected = DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
        def dependency = Mock(DependencyMetadata)
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = new DefaultBuildableComponentSelectionResult()

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, dependency.getRequested())

        then:
        _ * dependency.getRequested() >> selector
        _ * a.version >> "1.2"
        _ * b.version >> "1.3"
        _ * b.id >> selected
        _ * c.version >> "2.0"
        _ * componentSelectionRules.rules >> rules({ComponentSelection selection ->
            if (selection.candidate.version != '1.3') {
                selection.reject("rejected")
            }
        })
        0 * _

        then:
        selectedComponentResult.match == selected
    }

    def "rejects dynamic version by rule with metadata" () {
        given:
        def selector = DefaultModuleVersionSelector.of("group", "name", "latest.release")
        def selected = DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
        def dependency = Mock(DependencyMetadata)
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = new DefaultBuildableComponentSelectionResult()

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, dependency.getRequested())

        then:
        _ * dependency.requested >> selector
        _ * dependency.withRequestedVersion(_) >> Stub(DependencyMetadata)
        _ * a.version >> "1.2"
        _ * b.version >> "1.3"
        _ * b.id >> selected
        _ * c.version >> "2.0"
        1 * c.resolve() >> resolvedWithStatus("milestone")
        1 * b.resolve() >> resolvedWithStatus("release")
        1 * componentSelectionRules.rules >> rules({ComponentSelection selection ->
            if (selection.candidate.version == '1.3') {
                selection.reject("rejected")
            }
        })
        0 * _

        then:
        // Since 1.3 is "latest.release" but it's rejected by rule, we should fail to resolve
        selectedComponentResult.state == NoMatch
    }

    def "returns no match when no versions match without metadata"() {
        given:
        def selector = DefaultModuleVersionSelector.of("group", "name", "1.1")
        def dependency = Mock(DependencyMetadata)
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = new DefaultBuildableComponentSelectionResult()

        when:
        chooser.selectNewestMatchingComponent([c, b, a], selectedComponentResult, dependency.getRequested())

        then:
        _ * dependency.requested >> selector
        _ * componentSelectionRules.rules >> []
        _ * a.version >> "1.2"
        _ * b.version >> "1.3"
        _ * c.version >> "2.0"
        0 * _

        and:
        selectedComponentResult.state == NoMatch
    }

    def "returns no match when no versions are chosen with metadata"() {
        given:
        def selector = DefaultModuleVersionSelector.of("group", "name", "latest.release")
        def dependency = Mock(DependencyMetadata)
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = new DefaultBuildableComponentSelectionResult()

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, dependency.getRequested())

        then:
        _ * dependency.getRequested() >> selector
        _ * a.version >> "1.2"
        _ * b.version >> "1.3"
        _ * c.version >> "2.0"
        1 * a.resolve() >> resolvedWithStatus("integration")
        1 * b.resolve() >> resolvedWithStatus("integration")
        1 * c.resolve() >> resolvedWithStatus("integration")
        _ * componentSelectionRules.rules >> []
        0 * _

        then:
        selectedComponentResult.state == NoMatch
    }

    def "returns no match when all matching versions match are rejected by rule"() {
        given:
        def selector = DefaultModuleVersionSelector.of("group", "name", "+")
        def dependency = Mock(DependencyMetadata)
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = new DefaultBuildableComponentSelectionResult()

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, dependency.getRequested())

        then:
        _ * dependency.getRequested() >> selector
        _ * a.version >> "1.2"
        _ * a.id >> Stub(ModuleComponentIdentifier)
        _ * b.version >> "1.3"
        _ * b.id >> Stub(ModuleComponentIdentifier)
        _ * c.version >> "2.0"
        _ * c.id >> Stub(ModuleComponentIdentifier)
        _ * componentSelectionRules.rules >> rules({ ComponentSelection selection ->
            selection.reject("Rejecting everything")
        })
        0 * _

        then:
        selectedComponentResult.state == NoMatch
    }

    def "stops when candidate cannot be resolved"() {
        given:
        def selector = DefaultModuleVersionSelector.of("group", "name", "latest.release")
        def dependency = Mock(DependencyMetadata)
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = new DefaultBuildableComponentSelectionResult()

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, dependency.getRequested())

        then:
        _ * dependency.getRequested() >> selector
        _ * a.version >> "1.2"
        _ * b.version >> "1.3"
        _ * c.version >> "2.0"
        1 * c.resolve() >> resolvedWithStatus("integration")
        1 * b.resolve() >> resolvedWithFailure()
        _ * componentSelectionRules.rules >> []
        0 * _

        then:
        selectedComponentResult.state == Failed
    }

    def resolvedWithStatus(String status) {
        def meta = Stub(ModuleComponentResolveMetadata) {
            getStatusScheme() >> ["integration", "milestone", "release"]
            getStatus() >> status
        }
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
        result.resolved(meta)
        return result
    }

    def resolvedWithFailure() {
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
        result.failed(Stub(ModuleVersionResolveException))
        return result
    }

    def rules(Closure closure) {
        return [
            new SpecRuleAction<ComponentSelection>(
                    new ClosureBackedRuleAction<ComponentSelection>(ComponentSelection, closure),
                    Specs.<ComponentSelection>satisfyAll()
            )
        ]
    }
}
