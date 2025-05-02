/*
 * Copyright 2024 the original author or authors.
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

package gradlebuild.buildutils.tasks

import spock.lang.Specification

class UpdateKotlinVersionsTest extends Specification {

    def previousVersions = [
        "1.9.25", "1.9.24", "1.9.20", "1.9.20-RC2", "1.9.0", "1.9.0-Beta",
        "1.8.22, 1.8.0, 1.8.0-Beta",
    ]
    def minimumSupported = "1.9.10"

    def "latest patch version"() {
        given:
        def allVersions = ["2.0.30", "2.0.20", "2.0.10", "2.0.0"] + previousVersions

        when:
        def selected = UpdateKotlinVersions.selectVersionsFrom(minimumSupported, allVersions)

        then:
        selected == ["1.9.10", "1.9.25", "2.0.0", "2.0.30"]
    }

    def "beta of latest patch version"() {
        given:
        def allVersions = [
            "2.0.30-Beta2", "2.0.30-Beta1",
            "2.0.20", "2.0.20-Beta1",
            "2.0.10", "2.0.10-Beta1",
            "2.0.0", "2.0.0-RC1", "2.0.0-Beta1",
        ] + previousVersions

        when:
        def selected = UpdateKotlinVersions.selectVersionsFrom(minimumSupported, allVersions)

        then:
        selected == ["1.9.10", "1.9.25", "2.0.0", "2.0.20", "2.0.30-Beta2"]
    }


    def "rc of latest patch version"() {
        given:
        def allVersions = [
            "2.0.30-RC1", "2.0.30-Beta1",
            "2.0.20",
            "2.0.10",
            "2.0.0", "2.0.0-RC1", "2.0.0-Beta1",
        ] + previousVersions

        when:
        def selected = UpdateKotlinVersions.selectVersionsFrom(minimumSupported, allVersions)

        then:
        selected == ["1.9.10", "1.9.25", "2.0.0", "2.0.20", "2.0.30-RC1"]
    }

    def "beta and rc of two latest patch versions"() {
        given:
        def allVersions = [
            "2.0.40-Beta2", "2.0.40-Beta1",
            "2.0.30-RC1", "2.0.30-Beta1",
            "2.0.20", "2.0.20-RC1",
            "2.0.10", "2.0.10-RC1",
            "2.0.0", "2.0.0-RC1", "2.0.0-Beta1",
        ] + previousVersions

        when:
        def selected = UpdateKotlinVersions.selectVersionsFrom(minimumSupported, allVersions)

        then:
        selected == ["1.9.10", "1.9.25", "2.0.0", "2.0.20", "2.0.30-RC1", "2.0.40-Beta2"]
    }
}
