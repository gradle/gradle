/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.vfs.impl

import org.gradle.internal.snapshot.CaseSensitivity
import spock.lang.Specification

class VersionHierarchyRootTest extends Specification {
    def versionHierarchyRoot = VersionHierarchyRoot.empty(0, CaseSensitivity.CASE_SENSITIVE)

    def "correct versions for: initial state: #initialState, invalidate location: #invalidateLocation"() {
        updateVersions(*initialState)

        def changedLocationsWithAncestors = changedLocations.collectMany { locationWithAncestors(it) } as Set
        def versionsBefore = (changedLocationsWithAncestors + unchangedLocations + invalidateLocation).<String, Long, String> collectEntries {
            [(it): getVersion(it)]
        }
        def versionAtRootBefore = getVersion("/")
        when:
        updateVersions(invalidateLocation)
        then:
        changedLocationsWithAncestors.forEach {
            assert getVersion(it) > versionsBefore[it]
            assert getVersion(it) > versionAtRootBefore
        }
        unchangedLocations.forEach {
            assert getVersion(it) == versionsBefore[it]
            assert getVersion(it) <= versionAtRootBefore
        }
        getVersion(invalidateLocation) > versionsBefore[invalidateLocation]

        where:
        initialState                                           | invalidateLocation              | changedLocations                            | unchangedLocations
        ['/my/some/child']                                     | '/my/some'                      | ['/my/some/child']                          | ['/my/other', '/my/other/child']
        ['/my/some/child', '/my/other/child']                  | '/my/some'                      | ['/my/some/child']                          | ['/my/other', '/my/other/child']
        ['/my/child1', '/my/child2', '/my/child1/other/child'] | '/my'                           | ['/my/child1/other/child', '/my/child2']    | ['/other']
        ['/my/child1', '/my/child2', '/my/child1/other/child'] | '/my/child1/other/child/inside' | ['/my/child1/other/child/inside']           | ['/my/other', '/my/child2']
        ['/my/child1', '/my/child2', '/my/child1/other/child'] | '/my/child1'                    | ['/my/child1/other/child']                  | ['/my/other', '/my/child2']
        ['/my/child1', '/my/child2', '/my/child1/other/child'] | '/my/new'                       | ['/my/new/inside']                          | ['/my/child1', '/my/other', '/my/child2']
        ['/my/child1', '/my/child2', '/my/child1/other/child'] | '/my/child2'                    | ['/my/child2/inside']                       | ['/my/child1', '/my/other', '/my/child1/other/child']
        ['/my/some/child1']                                    | '/my/some/child2'               | ['/my/some/child2']                         | ['/my/some/child1', '/my/other']
        ['/my/some/child1', '/my/other']                       | '/my/some/child2'               | ['/my/some/child2']                         | ['/my/some/child1', '/my/other']
        ['/my/some/initialChild']                              | '/my/some'                      | ['/my/some/child', '/my/some/initialChild'] | ['/my/other']
    }

    def "can query and update the root '#root'"() {
        def locations = ['/my/path', '/my/sibling', '/my/path/some/child']
        updateVersions('/my/path', '/my/sibling', '/my/path/some/child')

        when:
        def rootVersionBefore = getVersion('')
        then:
        getVersion('/') == rootVersionBefore

        when:
        updateVersions(root)
        then:
        (locations + ['/', '']).collect { getVersion(it) }.every { it == rootVersionBefore + 1 }

        where:
        root << ['', '/']
    }

    private long getVersion(String location) {
        return versionHierarchyRoot.getVersion(location)
    }

    List<String> locationWithAncestors(String location) {
        (location.split('/') as List)
            .findAll {!it.empty }
            .inits().collect { "/${it.join('/')}".toString() }
    }

    private void updateVersions(String... locations) {
        VersionHierarchyRoot newVersionHierarchyRoot = versionHierarchyRoot
        for (location in locations) {
            newVersionHierarchyRoot = newVersionHierarchyRoot.updateVersion(location)
        }
        versionHierarchyRoot = newVersionHierarchyRoot
    }
}
