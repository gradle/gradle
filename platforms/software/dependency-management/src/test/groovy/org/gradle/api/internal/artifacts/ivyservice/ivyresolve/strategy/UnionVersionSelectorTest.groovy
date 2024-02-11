/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ModuleVersionIdentifier
import spock.lang.Specification

class UnionVersionSelectorTest extends Specification {
    private final VersionSelectorScheme versionSelectorScheme = new DefaultVersionSelectorScheme(new DefaultVersionComparator(), new VersionParser())
    private final VersionParser parser = new VersionParser()
    private final List<VersionSelector> members = []
    private final UnionVersionSelector union = new UnionVersionSelector(members)

    def "accept version '#candidate' with union of #selectors matches is #accept"() {
        when:
        withSelectors(selectors)

        then:
        union.accept(candidate) == accept

        and:
        union.accept(versionOf(candidate)) == accept

        and:
        union.accept(metadataFor(candidate)) == accept

        where:
        selectors              | candidate | accept
        ['1.0', '1.1']         | '1.0'     | true
        ['1.0', '1.1']         | '1.1'     | true
        ['1.0', '1.1']         | '1.2'     | false
        ['1.0', '[1,)']        | '1.2'     | true
        ['1.0', '1.1', '(,2]'] | '1.2'     | true
        ['1.0', '1.1', '(,2)'] | '2.0'     | false
    }

    def "union selector respects selectors #selectors flags semantics"() {
        when:
        withSelectors(selectors)

        then:
        union.isDynamic() == members.any { it.dynamic }

        and:
        union.requiresMetadata() == members.any { it.requiresMetadata() }

        and:
        union.matchesUniqueVersion() == members.every { it.matchesUniqueVersion() }

        and:
        union.canShortCircuitWhenVersionAlreadyPreselected() == members.every { it.canShortCircuitWhenVersionAlreadyPreselected() }

        where:
        selectors << [
            ['1.0'],
            ['1.0', '1.1'],
            ['1.0', '1+'],
            ['1.0', 'latest.release'],
            ['1+', 'latest.release'],
            ['(1,)', 'latest.release'],
            ['(1,)', 'latest.release', '2.0'],
            ['latest.milestone', 'latest.release'],
            ['1.+', '2.0', '[1.0,2.0]'],
        ]
    }

    private void withSelectors(List<String> selectors) {
        selectors.each {
            members.add(selectorOf(it))
        }
    }

    private VersionSelector selectorOf(String selectorString) {
        versionSelectorScheme.parseSelector(selectorString)
    }

    private Version versionOf(String candidate) {
        parser.transform(candidate)
    }

    private ComponentMetadata metadataFor(String candidate) {
        Mock(ComponentMetadata) {
            getId() >> Mock(ModuleVersionIdentifier) {
                getVersion() >> candidate
            }
        }
    }
}
