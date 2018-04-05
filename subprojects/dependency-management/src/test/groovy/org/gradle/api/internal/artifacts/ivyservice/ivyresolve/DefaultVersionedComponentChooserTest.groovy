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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.result.ComponentSelectionContext
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.rules.ClosureBackedRuleAction
import org.gradle.internal.rules.SpecRuleAction
import spock.lang.Specification

class DefaultVersionedComponentChooserTest extends Specification {
    def versionSelectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator())
    def versionComparator = new DefaultVersionComparator()
    def componentSelectionRules = Mock(ComponentSelectionRulesInternal)

    def chooser = new DefaultVersionedComponentChooser(versionComparator, componentSelectionRules)

    def "chooses latest version for component meta data"() {
        def one = Stub(ComponentResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }
        def two = Stub(ComponentResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.1")
        }
        def three = Stub(ComponentResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.2")
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
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }
        def two = Mock(ComponentResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }

        when:
        1 * one.missing >> true
        1 * two.missing >> false
        0 * componentSelectionRules.apply(_,_)

        then:
        chooser.selectNewestComponent(one, two) == two

        when:
        1 * one.missing >> false
        0 * componentSelectionRules.apply(_,_)

        then:
        chooser.selectNewestComponent(one, two) == one
    }

    def "chooses newest matching version without requiring metadata"() {
        given:
        def selected = DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("1.+"), null)

        then:
        _ * a.version >> version("1.2")
        _ * a.id >> DefaultModuleComponentIdentifier.newId("group", "name", "1.2")
        _ * b.version >> version("1.3")
        _ * b.id >> selected
        _ * c.version >> version("2.0")
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.notMatched('2.0')
        0 * selectedComponentResult.notMatched('1.2') // versions are checked latest first
        1 * selectedComponentResult.matches(selected)
        0 * _

    }

    def "chooses newest non rejected matching version without requiring metadata"() {
        given:
        def selected = DefaultModuleComponentIdentifier.newId("group", "name", "1.2")
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def d = Mock(ModuleComponentResolveState)
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, d, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("1.+"), versionSelectorScheme.parseSelector("1.3"))

        then:
        _ * a.version >> version("1.2")
        _ * a.id >> DefaultModuleComponentIdentifier.newId("group", "name", "1.2")
        _ * b.version >> version("1.3")
        _ * b.id >> selected
        _ * c.version >> version("2.0")
        _ * d.version >> version("1.1")
        _ * d.id >> DefaultModuleComponentIdentifier.newId("group", "name", "1.1")
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.notMatched('2.0')
        1 * selectedComponentResult.rejected('1.3')
        0 * selectedComponentResult.notMatched('1.1') // versions are checked latest first
        1 * selectedComponentResult.matches(selected)
        0 * _

    }

    def "chooses newest matching version requiring metadata"() {
        given:
        def selected = DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def dependency = Mock(DependencyMetadata)
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("latest.milestone"), null)

        then:
        _ * a.version >> version("1.2")
        _ * b.version >> version("1.3")
        _ * b.id >> selected
        _ * c.version >> version("2.0")
        1 * c.resolve() >> resolvedWithStatus("integration")
        1 * c.getComponentMetadataSupplier()
        1 * b.resolve() >> resolvedWithStatus("milestone")
        1 * b.getComponentMetadataSupplier()
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.notMatched('2.0')
        1 * selectedComponentResult.matches(selected)
        0 * _

    }

    def "chooses newest non rejected matching version requiring metadata"() {
        given:
        def selected = DefaultModuleComponentIdentifier.newId("group", "name", "1.2")
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("latest.milestone"), versionSelectorScheme.parseSelector('1.3'))

        then:
        _ * a.version >> version("1.2")
        _ * b.version >> version("1.3")
        _ * a.id >> selected
        _ * c.version >> version("2.0")
        1 * c.resolve() >> resolvedWithStatus("integration")
        1 * c.getComponentMetadataSupplier()
        1 * b.getComponentMetadataSupplier()
        1 * b.resolve() >> resolvedWithStatus("milestone")
        1 * b.getId() >> DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
        1 * a.getComponentMetadataSupplier()
        1 * a.resolve() >> resolvedWithStatus("milestone")
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.notMatched('2.0')
        1 * selectedComponentResult.rejected('1.3')
        1 * selectedComponentResult.matches(selected)
        0 * _

    }

    def "rejects dynamic version by rule without metadata" () {
        given:
        def selected = DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def d = Mock(ModuleComponentResolveState)
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([d, c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("1.+"), null)

        then:
        _ * a.version >> version("1.2")
        _ * b.version >> version("1.3")
        _ * b.id >> selected
        _ * c.version >> version("2.0")
        _ * d.version >> version("1.4")
        _ * d.id >> DefaultModuleComponentIdentifier.newId("group", "name", "1.4")
        _ * componentSelectionRules.rules >> rules({ComponentSelection selection ->
            if (selection.candidate.version != '1.3') {
                selection.reject("rejected")
            }
        })
        1 * selectedComponentResult.notMatched('2.0')
        1 * selectedComponentResult.rejected('1.4') // 1.2 won't be rejected because of latest first sorting
        1 * selectedComponentResult.matches(selected)
        0 * _
    }

    def "rejects dynamic version by rule with metadata" () {
        given:
        def selected = DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("latest.release"), null)

        then:
        _ * a.version >> version("1.2")
        _ * b.version >> version("1.3")
        _ * b.id >> selected
        _ * c.version >> version("2.0")
        1 * c.resolve() >> resolvedWithStatus("milestone")
        1 * c.getComponentMetadataSupplier()
        1 * b.resolve() >> resolvedWithStatus("release")
        1 * b.getComponentMetadataSupplier()
        1 * componentSelectionRules.rules >> rules({ComponentSelection selection ->
            if (selection.candidate.version == '1.3') {
                selection.reject("rejected")
            }
        })
        1 * selectedComponentResult.notMatched('2.0')
        1 * selectedComponentResult.rejected('1.3')
        1 * selectedComponentResult.noMatchFound()
        0 * _

    }

    def "returns no match when no versions match without metadata"() {
        given:
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, b, a], selectedComponentResult, versionSelectorScheme.parseSelector("1.1"), null)

        then:
        _ * componentSelectionRules.rules >> []
        _ * a.version >> version("1.2")
        _ * b.version >> version("1.3")
        _ * c.version >> version("2.0")
        1 * selectedComponentResult.notMatched('2.0')
        1 * selectedComponentResult.notMatched('1.3')
        1 * selectedComponentResult.notMatched('1.2')
        1 * selectedComponentResult.noMatchFound()
        0 * _

    }

    def "returns no match when no versions are chosen with metadata"() {
        given:
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("latest.release"), null)

        then:
        _ * a.version >> version("1.2")
        _ * b.version >> version("1.3")
        _ * c.version >> version("2.0")
        1 * a.resolve() >> resolvedWithStatus("integration")
        1 * a.getComponentMetadataSupplier()
        1 * b.resolve() >> resolvedWithStatus("integration")
        1 * b.getComponentMetadataSupplier()
        1 * c.resolve() >> resolvedWithStatus("integration")
        1 * c.getComponentMetadataSupplier()
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.notMatched('2.0')
        1 * selectedComponentResult.notMatched('1.3')
        1 * selectedComponentResult.notMatched('1.2')
        1 * selectedComponentResult.noMatchFound()
        0 * _

    }

    def "returns no match when all matching versions match are rejected by rule"() {
        given:
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("+"), null)

        then:
        _ * a.version >> version("1.2")
        _ * a.id >> Stub(ModuleComponentIdentifier)
        _ * b.version >> version("1.3")
        _ * b.id >> Stub(ModuleComponentIdentifier)
        _ * c.version >> version("2.0")
        _ * c.id >> Stub(ModuleComponentIdentifier)
        _ * componentSelectionRules.rules >> rules({ ComponentSelection selection ->
            selection.reject("Rejecting everything")
        })
        1 * selectedComponentResult.rejected('2.0')
        1 * selectedComponentResult.rejected('1.3')
        1 * selectedComponentResult.rejected('1.2')
        1 * selectedComponentResult.noMatchFound()
        0 * _
    }

    def "stops when candidate cannot be resolved"() {
        given:
        def a = Mock(ModuleComponentResolveState)
        def b = Mock(ModuleComponentResolveState)
        def c = Mock(ModuleComponentResolveState)
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("latest.release"), null)

        then:
        _ * a.version >> version("1.2")
        _ * b.version >> version("1.3")
        _ * c.version >> version("2.0")
        1 * c.resolve() >> resolvedWithStatus("integration")
        1 * c.getComponentMetadataSupplier()
        1 * b.resolve() >> resolvedWithFailure()
        1 * b.getComponentMetadataSupplier()
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.notMatched('2.0')
        1 * selectedComponentResult.failed(_ as ModuleVersionResolveException)
        0 * _

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

    def version(String version) {
        return VersionParser.INSTANCE.transform(version)
    }
}
