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

package org.gradle.internal.watch.vfs.impl

import org.gradle.internal.snapshot.CaseSensitivity
import spock.lang.Specification

class LocationsWrittenByCurrentBuildTest extends Specification {
    def locationsWrittenByCurrentBuild = new LocationsWrittenByCurrentBuild(CaseSensitivity.CASE_SENSITIVE)

    def setup() {
        locationsWrittenByCurrentBuild.buildStarted()
    }

    def "#description change implies #result"() {
        locationsWrittenByCurrentBuild.locationsWritten(['/my/path', '/my/sibling', '/my/path/some/child'])


        def versionBefore = locationsWrittenByCurrentBuild.getVersionFor("/my/path")
        when:
        locationsWrittenByCurrentBuild.locationsWritten([locationWritten])
        then:
        if (increasesVersion) {
            assert locationsWrittenByCurrentBuild.getVersionFor('/my/path') > versionBefore
        } else {
            assert locationsWrittenByCurrentBuild.getVersionFor('/my/path') == versionBefore
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

    def "can query and update the root '#root'"() {
        def locations = ['/my/path', '/my/sibling', '/my/path/some/child']
        locationsWrittenByCurrentBuild.locationsWritten(['/my/path', '/my/sibling', '/my/path/some/child'])

        def versionsBefore = (locations + ['/', '']).collect { locationsWrittenByCurrentBuild.getVersionFor(it) }
        when:
        locationsWrittenByCurrentBuild.locationsWritten(['/'])
        then:
        (locations + ['/', '']).collect { locationsWrittenByCurrentBuild.getVersionFor(it) } == versionsBefore.collect { it + 1 }

        where:
        root << ['', '/']
    }
}
