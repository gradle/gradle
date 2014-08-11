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

import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.VersionSelection
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.VersionSelectionRulesInternal
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import spock.lang.Specification
import spock.lang.Unroll

class NewestVersionComponentChooserTest extends Specification {
    def versionMatcher = Mock(VersionMatcher)
    def latestStrategy = Mock(LatestStrategy)
    def versionSelectionRules = Mock(VersionSelectionRulesInternal)

    def chooser = new NewestVersionComponentChooser(latestStrategy, versionMatcher, versionSelectionRules)

    def "uses version matcher to determine if selector can select multiple components"() {
        def selector = Mock(ModuleVersionSelector)
        when:
        1 * selector.version >> "foo"
        versionMatcher.isDynamic("foo") >> false
        versionSelectionRules.hasRules() >> false

        then:
        !chooser.canSelectMultipleComponents(selector)

        when:
        1 * selector.version >> "bar"
        versionMatcher.isDynamic("bar") >> true

        then:
        chooser.canSelectMultipleComponents(selector)
    }

    def "uses version selection rules to determine if selector can select multiple components"() {
        def selector = Mock(ModuleVersionSelector)
        when:
        1 * selector.version >> "foo"
        versionMatcher.isDynamic("foo") >> false
        1 * versionSelectionRules.hasRules() >> false

        then:
        !chooser.canSelectMultipleComponents(selector)

        when:
        1 * selector.version >> "bar"
        versionMatcher.isDynamic("bar") >> false
        versionSelectionRules.hasRules() >> true

        then:
        chooser.canSelectMultipleComponents(selector)
    }

    def "chooses latest version for component meta data"() {
        def one = Stub(ComponentMetaData) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }
        def two = Stub(ComponentMetaData) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.1")
        }

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> -1
        0 * versionSelectionRules.apply(_)

        then:
        chooser.choose(one, two) == two

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> 1
        0 * versionSelectionRules.apply(_)

        then:
        chooser.choose(one, two) == one
    }

    def "chooses non-generated descriptor over generated"() {
        def one = Mock(ComponentMetaData) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }
        def two = Mock(ComponentMetaData) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.1")
        }

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> 0
        1 * one.generated >> true
        1 * two.generated >> false
        0 * versionSelectionRules.apply(_)

        then:
        chooser.choose(one, two) == two

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> 0
        1 * one.generated >> false
        0 * versionSelectionRules.apply(_)

        then:
        chooser.choose(one, two) == one
    }

    def "chooses newest matching version without requiring metadata"() {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "1.+")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        1 * versionMatcher.needModuleMetadata("1.+") >> false
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * versionMatcher.accept("1.+", "2.0") >> false
        1 * versionMatcher.accept("1.+", "1.3") >> true
        2 * versionSelectionRules.apply(_)
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
    }

    @Unroll
    def "chooses dynamic version selected by rule (#operation #triggerVersion) without metadata" () {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "1.+")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        1 * versionMatcher.needModuleMetadata("1.+") >> false
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        versionMatcherCalls * versionMatcher.accept("1.+", _) >> { pattern, version ->
            // 1.+ should return 1.3 according to this version matcher
            switch(version) {
                case "2.0": return false
                case "1.3": return true
                case "1.2": return true
                default: return false
            }
        }
        rulesApplyCalls * versionSelectionRules.apply(_) >> { VersionSelection selection ->
            if (selection.candidate.version == triggerVersion) {
                selection."${operation}"()
            }
        }
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == DefaultModuleComponentIdentifier.newId("group", "name", "${expectedVersion}")

        where:
        // when we see the triggerVersion we should do the operation and then get the expectedVersion as the end result
        triggerVersion | operation | expectedVersion | versionMatcherCalls | rulesApplyCalls
        '2.0'          | "accept"  | '2.0'           | 0                   | 1
        '1.3'          | "reject"  | '1.2'           | 2                   | 3
    }

    @Unroll
    def "chooses dynamic version selected by rule (#operation #triggerVersion) with metadata" () {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "latest.integration")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        _ * dependency.withRequestedVersion(_) >> Stub(DependencyMetaData)
        _ * repo.resolveComponentMetaData(_, _, _) >> { moduleVersionDep, candidateId, DefaultBuildableModuleVersionMetaDataResolveResult result ->
            result.resolved(Stub(MutableModuleVersionMetaData) {
                getComponentId() >> { candidateId }
            }, null)
        }
        1 * versionMatcher.needModuleMetadata("latest.integration") >> true
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        versionMatcherCalls * versionMatcher.accept("latest.integration", _) >> { pattern, MutableModuleVersionMetaData metadata ->
            // latest.integration should return 1.3 according to this version matcher
            switch(metadata.componentId.version) {
                case "2.0": return false
                case "1.3": return true
                case "1.2": return true
                default: return false
            }
        }
        rulesApplyCalls * versionSelectionRules.apply(_) >> { VersionSelection selection ->
            if (selection.candidate.version == triggerVersion) {
                selection."${operation}"()
            }
        }
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == DefaultModuleComponentIdentifier.newId("group", "name", "${expectedVersion}")

        where:
        // when we see the triggerVersion we should do the operation and then get the expectedVersion as the end result
        triggerVersion | operation | expectedVersion | versionMatcherCalls | rulesApplyCalls
        '2.0'          | "accept"  | '2.0'           | 0                   | 1
        '1.3'          | "reject"  | '1.2'           | 2                   | 3
    }

    def "rejects static version by selection rule" () {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "1.3")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        1 * versionMatcher.needModuleMetadata("1.3") >> false
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * versionMatcher.accept("1.3", "2.0") >> false
        // Should not get a versionMatcher.accept call for 1.3
        0 * versionMatcher.accept("1.3", "1.3")
        1 * versionMatcher.accept("1.3", "1.2") >> false
        3 * versionSelectionRules.apply(_) >> { VersionSelection selection ->
            if (selection.candidate.version == '1.3') {
                selection.reject()
            }
        }
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == null
    }

    def "substitutes static version by selection rule" () {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "1.3")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        1 * versionMatcher.needModuleMetadata("1.3") >> false
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * versionMatcher.accept("1.3", "2.0") >> false
        // Should not get a versionMatcher.accept call for 1.3 or 1.2
        0 * versionMatcher.accept("1.3", "1.3")
        0 * versionMatcher.accept("1.3", "1.2")
        3 * versionSelectionRules.apply(_) >> { VersionSelection selection ->
            switch(selection.candidate.version) {
                case '1.3':
                    selection.reject()
                    break;
                case '1.2':
                    selection.accept()
                    break;
                default:
                    break;
            }
        }
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == DefaultModuleComponentIdentifier.newId("group", "name", "1.2")
    }

    def "returns null when no versions are chosen without metadata"() {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "1.3")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        1 * versionMatcher.needModuleMetadata("1.3") >> false
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        3 * versionMatcher.accept("1.3", _) >> false
        3 * versionSelectionRules.apply(_)
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == null
    }

    def "returns null when no versions are chosen with metadata"() {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "latest.integration")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        _ * dependency.withRequestedVersion(_) >> Stub(DependencyMetaData)
        _ * repo.resolveComponentMetaData(_, _, _) >> { moduleVersionDep, candidateId, DefaultBuildableModuleVersionMetaDataResolveResult result ->
            result.resolved(Stub(MutableModuleVersionMetaData) {
                getComponentId() >> { candidateId }
            }, null)
        }
        1 * versionMatcher.needModuleMetadata("latest.integration") >> true
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        3 * versionMatcher.accept("latest.integration", _) >> false
        3 * versionSelectionRules.apply(_)
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == null
    }
}
