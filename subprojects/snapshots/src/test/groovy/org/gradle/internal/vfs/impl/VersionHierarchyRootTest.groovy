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

    def "#description change implies #result"() {
        updateVersions('/my/path', '/my/sibling', '/my/path/some/child')


        def versionBefore = versionHierarchyRoot.getVersion("/my/path")
        def versionAtRootBefore = versionHierarchyRoot.getVersion('')
        when:
        updateVersions(locationWritten)
        then:
        if (increasesVersion) {
            assert versionHierarchyRoot.getVersion('/my/path') > versionBefore
            assert versionHierarchyRoot.getVersion('/my/path') > versionAtRootBefore
        } else {
            assert versionHierarchyRoot.getVersion('/my/path') == versionBefore
            assert versionHierarchyRoot.getVersion('/my/path') <= versionAtRootBefore
        }

        where:
        description     | locationWritten              | increasesVersion
        'parent'        | '/my'                        | true
        'child'         | '/my/path/some/child/inside' | true
        'same location' | '/my/path'                   | true
        'new sibling'   | '/my/new-sibling'            | false
        'sibling'       | '/my/sibling'                | false
        result = increasesVersion ? 'newer version' : 'same version'
    }

    def "does not update siblings"() {
        updateVersions('/my', '/my/some/location')

        def versionBefore = versionHierarchyRoot.getVersion('/my/some/location')
        when:
        updateVersions('/my/some/sibling')
        then:
        versionHierarchyRoot.getVersion('/my/some/location') == versionBefore
    }

    def "can query and update the root '#root'"() {
        def locations = ['/my/path', '/my/sibling', '/my/path/some/child']
        updateVersions('/my/path', '/my/sibling', '/my/path/some/child')

        when:
        def rootVersionBefore = versionHierarchyRoot.getVersion('')
        then:
        versionHierarchyRoot.getVersion('/') == rootVersionBefore

        when:
        updateVersions(root)
        then:
        (locations + ['/', '']).collect { versionHierarchyRoot.getVersion(it) }.every { it == rootVersionBefore + 1 }

        where:
        root << ['', '/']
    }

    private void updateVersions(String... locations) {
        VersionHierarchyRoot newVersionHierarchyRoot = versionHierarchyRoot
        for (location in locations) {
            newVersionHierarchyRoot = newVersionHierarchyRoot.touch(location)
        }
        versionHierarchyRoot = newVersionHierarchyRoot
    }
}
