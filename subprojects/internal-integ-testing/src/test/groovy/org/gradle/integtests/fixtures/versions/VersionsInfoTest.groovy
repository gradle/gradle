/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures.versions

import org.gradle.util.GradleVersion
import spock.lang.Specification
import static org.gradle.util.GradleVersion.version

/**
 * by Szczepan Faber, created at: 3/12/12
 */
class VersionsInfoTest extends Specification {

    def info = new VersionsInfo()

    def "reads versions information from the actual resource"() {
        expect:
        !info.versions.empty
        info.versions.size() > 5
        info.versions.find { "1.0-milestone-3"  }
        info.versions.find { "1.0-milestone-8a" }
    }

    def "versions are ordered latest first"() {
        expect:
        version(info.versions[0]) > version(info.versions[1])
        info.versions == info.versions.sort( { a,b -> version(b).compareTo(version(a)) })
    }
    
    def "contains release candidate if not yet released"() {
        when:
        info.getVersionsJson = {[
            [version: "1.0-milestone-8a", current:true, nightly:false, rcFor:null],
            [version: "1.0-milestone-9-20120309103546+0100", current:false, nightly:false, rcFor:"1.0-milestone-9"],
            [version: "1.0-rc-1-20120312000043+0100", current:false, nightly:true, rcFor:null],
            [version: "1.0-milestone-8", current:false, nightly:false, rcFor:null],
            [version: "1.0-milestone-7", current:false, nightly:false, rcFor:null]
        ]}

        then:
        info.versions == ["1.0-milestone-9-20120309103546+0100", "1.0-milestone-8a", "1.0-milestone-8", "1.0-milestone-7"]
    }

    def "excludes release candidate if already released"() {
        when:
        info.getVersionsJson = {[
            [version: "1.0-milestone-9", current:true, nightly:false, rcFor:null],
            [version: "1.0-milestone-9-20120309103546+0100", current:false, nightly:false, rcFor:"1.0-milestone-9"],
            [version: "1.0-rc-1-20120312000043+0100", current:false, nightly:true, rcFor:null],
            [version: "1.0-milestone-8", current:false, nightly:false, rcFor:null],
            [version: "1.0-milestone-7", current:false, nightly:false, rcFor:null]
        ]}

        then:
        info.versions == ["1.0-milestone-9", "1.0-milestone-8", "1.0-milestone-7"]
    }

    def "excludes certain versions"() {
        given:
        info.lowestInterestingVersion = "1.0-milestone-7"
        info.excludedVersions = ["1.0-milestone-8"]

        when:
        info.getVersionsJson = {[
            [version: "1.0-milestone-9", current:true, nightly:false, rcFor:null],
            [version: "1.0-milestone-8", current:false, nightly:false, rcFor:null],
            [version: "1.0-milestone-7", current:false, nightly:false, rcFor:null],
            [version: "1.0-milestone-6", current:false, nightly:false, rcFor:null],
            [version: "1.0-milestone-5", current:false, nightly:false, rcFor:null]
        ]}

        then:
        info.versions == ["1.0-milestone-9", "1.0-milestone-7"]
    }

    def "excludes current version"() {
        when:
        info.getVersionsJson = {[
                [version: GradleVersion.current().version, current:false, nightly:false, rcFor:null]
        ]}

        then:
        info.versions == []
    }
}
