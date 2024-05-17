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
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultCachePolicy
import org.gradle.api.internal.attributes.DefaultAttributesSchema
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveMetadata
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.model.AttributeMatcher
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor
import org.gradle.internal.resolve.result.ComponentSelectionContext
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.rules.ClosureBackedRuleAction
import org.gradle.internal.rules.SpecRuleAction
import org.gradle.util.AttributeTestUtil
import org.gradle.util.SnapshotTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultVersionedComponentChooserTest extends Specification {
    def versionParser = new VersionParser()
    def versionComparator = new DefaultVersionComparator()
    def versionSelectorScheme = new DefaultVersionSelectorScheme(versionComparator, versionParser)
    def componentSelectionRules = Mock(ComponentSelectionRulesInternal)
    def attributesSchema = new DefaultAttributesSchema(TestUtil.instantiatorFactory(), SnapshotTestUtil.isolatableFactory())
    def consumerAttributes = ImmutableAttributes.EMPTY
    def cachePolicy = new DefaultCachePolicy()

    def chooser = new DefaultVersionedComponentChooser(versionComparator, versionParser, componentSelectionRules, attributesSchema)

    def "chooses latest version for component meta data"() {
        def one = Stub(ModuleComponentGraphResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }
        def two = Stub(ModuleComponentGraphResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.1")
        }
        def three = Stub(ModuleComponentGraphResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.2")
        }

        when:
        0 * componentSelectionRules.apply(_, _)

        then:
        chooser.selectNewestComponent(one, two) == two

        when:
        0 * componentSelectionRules.apply(_, _)

        then:
        chooser.selectNewestComponent(two, three) == three
    }

    def "chooses non-generated descriptor over generated"() {
        def one = Mock(ModuleComponentGraphResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }
        def two = Mock(ModuleComponentGraphResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }

        when:
        1 * one.missing >> true
        1 * two.missing >> false
        0 * componentSelectionRules.apply(_, _)

        then:
        chooser.selectNewestComponent(one, two) == two

        when:
        1 * one.missing >> false
        0 * componentSelectionRules.apply(_, _)

        then:
        chooser.selectNewestComponent(one, two) == one
    }

    def "chooses newest matching version without requiring metadata"() {
        given:
        def a = component('1.2')
        def b = component('1.3')
        def c = component('2.0')
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("1.+"), null, consumerAttributes)

        then:
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.getContentFilter() >> null
        1 * selectedComponentResult.notMatched(c.id, _)
        0 * selectedComponentResult.notMatched(a.id, _) // versions are checked latest first
        1 * selectedComponentResult.matches(b.id)
        0 * _

    }

    def "chooses newest non rejected matching version without requiring metadata"() {
        given:
        def a = component('1.2')
        def b = component('1.3')
        def c = component('2.0')
        def d = component('1.1')
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, d, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("1.+"), versionSelectorScheme.parseSelector("1.3"), consumerAttributes)

        then:
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.getContentFilter() >> null
        1 * selectedComponentResult.notMatched(c.id, _)
        1 * selectedComponentResult.rejectedBySelector(b.id, _)
        0 * selectedComponentResult.notMatched(d.id, _) // versions are checked latest first
        1 * selectedComponentResult.matches(a.id)
        0 * _

    }

    def "chooses newest matching version requiring metadata"() {
        given:
        def a = component('1.2')
        def b = component('1.3', 'milestone')
        def c = component('2.0', 'integration')
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("latest.milestone"), null, consumerAttributes)

        then:
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.getContentFilter() >> null
        1 * selectedComponentResult.notMatched(c.id, _)
        1 * selectedComponentResult.matches(b.id)
        0 * _

    }

    def "chooses newest non rejected matching version requiring metadata"() {
        given:
        def a = component('1.2', 'milestone')
        def b = component('1.3', 'milestone')
        def c = component('2.0', 'integration')
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("latest.milestone"), versionSelectorScheme.parseSelector('1.3'), consumerAttributes)

        then:
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.getContentFilter() >> null
        1 * selectedComponentResult.notMatched(c.id, _)
        1 * selectedComponentResult.rejectedBySelector(b.id, _)
        1 * selectedComponentResult.matches(a.id)
        0 * _

    }

    def "rejects dynamic version by rule without metadata"() {
        given:
        def a = component('1.2')
        def b = component('1.3')
        def c = component('2.0')
        def d = component('1.4')
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([d, c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("1.+"), null, consumerAttributes)

        then:
        _ * componentSelectionRules.rules >> rules({ ComponentSelection selection ->
            if (selection.candidate.version != '1.3') {
                selection.reject("rejected")
            }
        })
        1 * selectedComponentResult.getContentFilter() >> null
        1 * selectedComponentResult.notMatched(c.id, _)
        1 * selectedComponentResult.rejectedByRule({it.id == d.id}) // 1.2 won't be rejected because of latest first sorting
        1 * selectedComponentResult.matches(b.id)
        0 * _
    }

    def "rejects dynamic version by rule with metadata"() {
        given:
        def a = component('1.2')
        def b = component('1.3', 'release')
        def c = component('2.0', 'milestone')
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("latest.release"), null, consumerAttributes)

        then:
        1 * componentSelectionRules.rules >> rules({ ComponentSelection selection ->
            if (selection.candidate.version == '1.3') {
                selection.reject("rejected")
            }
        })
        1 * selectedComponentResult.getContentFilter() >> null
        1 * selectedComponentResult.notMatched(c.id, _)
        1 * selectedComponentResult.rejectedByRule({it.id == b.id})
        1 * selectedComponentResult.noMatchFound()
        0 * _

    }

    def "rejects dynamic version by attributes with metadata using notation #notation"() {
        given:
        def a = component('1.2')
        def b = component('1.3', 'release', [color: 'red'])
        def c = component('1.4', 'release', [color: 'green'])
        def d = component('2.0', 'release', [color: 'blue'])
        def selectedComponentResult = Mock(ComponentSelectionContext)
        consumerAttributes = AttributeTestUtil.attributes(color: 'red')

        when:
        chooser.selectNewestMatchingComponent([c, a, d, b], selectedComponentResult, versionSelectorScheme.parseSelector(notation), null, consumerAttributes)

        then:
        1 * componentSelectionRules.getRules() >> []
        1 * selectedComponentResult.getContentFilter() >> null
        if (notation.indexOf('+') > 0) {
            1 * selectedComponentResult.notMatched(d.id, _)
        } else {
            1 * selectedComponentResult.doesNotMatchConsumerAttributes({
                it.id == d.id
                assertMatchingDescription(it.matchingDescription, 'red', 'blue')
            })
        }
        1 * selectedComponentResult.doesNotMatchConsumerAttributes({
            it.id == c.id
            assertMatchingDescription(it.matchingDescription, 'red', 'green')
        })
        1 * selectedComponentResult.matches(b.id)
        0 * _

        where:
        notation << ["[1.0,)", "latest.release", "1.+", "1+", "+"]
    }

    def assertMatchingDescription(List<AttributeMatcher.MatchingDescription> descriptions, String expectedRequestedValue, String expectedFound) {
        descriptions.find { it.requestedAttribute.name == 'color' }.with(true) {
            assert requestedValue.get() == expectedRequestedValue
            assert found.get() == expectedFound
        }
    }

    def "returns no match when no versions match without metadata"() {
        given:
        def a = component('1.2')
        def b = component('1.3')
        def c = component('2.0')
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, b, a], selectedComponentResult, versionSelectorScheme.parseSelector("1.1"), null, consumerAttributes)

        then:
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.getContentFilter() >> null
        1 * selectedComponentResult.notMatched(c.id, _)
        1 * selectedComponentResult.notMatched(b.id, _)
        1 * selectedComponentResult.notMatched(a.id, _)
        1 * selectedComponentResult.noMatchFound()
        0 * _

    }

    def "returns no match when no versions are chosen with metadata"() {
        given:
        def a = component('1.2', 'integration')
        def b = component('1.3', 'integration')
        def c = component('2.0', 'integration')
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("latest.release"), null, consumerAttributes)

        then:
        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.getContentFilter() >> null
        1 * selectedComponentResult.notMatched(c.id, _)
        1 * selectedComponentResult.notMatched(b.id, _)
        1 * selectedComponentResult.notMatched(a.id, _)
        1 * selectedComponentResult.noMatchFound()
        0 * _

    }

    def "returns no match when all matching versions match are rejected by rule"() {
        given:
        def a = component('1.2')
        def b = component('1.3')
        def c = component('2.0')
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("+"), null, consumerAttributes)

        then:
        _ * componentSelectionRules.rules >> rules({ ComponentSelection selection ->
            selection.reject("Rejecting everything")
        })
        1 * selectedComponentResult.getContentFilter() >> null
        1 * selectedComponentResult.rejectedByRule({it.id == c.id})
        1 * selectedComponentResult.rejectedByRule({it.id == b.id})
        1 * selectedComponentResult.rejectedByRule({it.id == a.id})
        1 * selectedComponentResult.noMatchFound()
        0 * _
    }

    def "stops when candidate cannot be resolved"() {
        given:
        def a = component('1.2')
        def b = Mock(ModuleComponentResolveState)
        def c = component('2.0', 'integration')
        def selectedComponentResult = Mock(ComponentSelectionContext)

        when:
        chooser.selectNewestMatchingComponent([c, a, b], selectedComponentResult, versionSelectorScheme.parseSelector("latest.release"), null, consumerAttributes)

        then:
        _ * b.version >> version("1.3")
        1 * b.resolve() >> resolvedWithFailure()
        1 * b.getComponentMetadataSupplier()

        _ * componentSelectionRules.rules >> []
        1 * selectedComponentResult.getContentFilter() >> null
        1 * selectedComponentResult.notMatched(c.id, _)
        1 * selectedComponentResult.failed(_ as ModuleVersionResolveException)
        0 * _

    }

    def resolvedWithStatus(String status, Map<String, ?> attributes) {
        def meta = Stub(ModuleComponentResolveMetadata) {
            getStatusScheme() >> ["integration", "milestone", "release"]
            getStatus() >> status
            getAttributes() >> AttributeTestUtil.attributes(attributes)
        }
        def state = Stub(ModuleComponentGraphResolveState) {
            getLegacyMetadata() >> meta
        }
        def result = new DefaultBuildableModuleComponentMetaDataResolveResult()
        result.resolved(state)
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
                Specs.<ComponentSelection> satisfyAll()
            )
        ]
    }

    ModuleComponentResolveState component(String v, String status = null, Map<String, ?> attributes = [:]) {
        def mid = DefaultModuleIdentifier.newId('group', 'name')
        def c = Stub(ModuleComponentResolveState) {
            getId() >> DefaultModuleComponentIdentifier.newId(mid, v)
            getVersion() >> version(v)
            if (status == null && attributes.isEmpty()) {
                resolve() >> { throw new RuntimeException("No metadata available") }
            } else {
                resolve() >> resolvedWithStatus(status, attributes)
            }
            getComponentMetadataSupplier() >> null
            getCachePolicy() >> cachePolicy
            getComponentMetadataSupplierExecutor() >> { componentMetadataSupplierExecutor() }
        }
        return c
    }

    ComponentMetadataSupplierRuleExecutor componentMetadataSupplierExecutor() {
        Mock(ComponentMetadataSupplierRuleExecutor) {
            execute(_, _, _, _, _) >> { args ->
                def (key, rule, converter, producer, cachePolicy) = args
                converter.transform(producer.transform(key, dependenciesProvider, null), dependenciesProvider, null)
            }
        }
    }

    def version(String version) {
        return versionParser.transform(version)
    }
}
