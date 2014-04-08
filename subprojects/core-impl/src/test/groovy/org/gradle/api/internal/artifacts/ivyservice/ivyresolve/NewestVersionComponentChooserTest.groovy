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
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import spock.lang.Specification

class NewestVersionComponentChooserTest extends Specification {
    def versionMatcher = Mock(VersionMatcher)
    def latestStrategy = Mock(LatestStrategy)

    def chooser = new NewestVersionComponentChooser(latestStrategy, versionMatcher)

    def "uses version matcher to determine if selector can select multiple components"() {
        def selector = Mock(ModuleVersionSelector)
        when:
        1 * selector.version >> "foo"
        versionMatcher.isDynamic("foo") >> false

        then:
        !chooser.canSelectMultipleComponents(selector)

        when:
        1 * selector.version >> "bar"
        versionMatcher.isDynamic("bar") >> true

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

        then:
        chooser.choose(one, two) == two

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> 1

        then:
        chooser.choose(one, two) == one
    }

    def "chooses non-generated descriptor over generated"() {
        def descriptorOne = Mock(ModuleDescriptor)
        def one = Stub(ComponentMetaData) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
            getDescriptor() >> descriptorOne
        }
        def descriptorTwo = Mock(ModuleDescriptor)
        def two = Stub(ComponentMetaData) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.1")
            getDescriptor() >> descriptorTwo
        }

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> 0
        1 * descriptorOne.default >> true
        1 * descriptorTwo.default >> false

        then:
        chooser.choose(one, two) == two

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> 0
        1 * descriptorOne.default >> false

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
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
    }
}
