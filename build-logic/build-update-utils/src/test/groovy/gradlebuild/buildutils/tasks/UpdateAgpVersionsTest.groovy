/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import spock.lang.Specification

class UpdateAgpVersionsTest extends Specification {

    def "selects matching gradle major versions when rc available (minimumSupported=#minimumSupported)"() {
        given:
        def gradleVersion = GradleVersion.version("9.2")
        def allVersions = [
            "8.8.0", "8.9.0",
            "9.0.0-alpha01", "9.0.0-beta01", "9.0.0-rc01"
        ].shuffled()

        when:
        def selected = UpdateAgpVersions.selectVersionsFrom(gradleVersion, null, allVersions)

        then:
        selected == ["8.9.0", "9.0.0-rc01"]
    }

    def "selects matching gradle major versions when stable or rc available (minimumSupported=#minimumSupported)"() {
        given:
        def gradleVersion = GradleVersion.version("9.2")
        def allVersions = [
            "8.8.0", "8.9.0",
            "9.7.0-alpha01", "9.7.0-beta01", "9.7.0-rc01", "9.7.0", "9.7.1",
            "9.8.0-alpha01", "9.8.0-beta01", "9.8.0-rc01", "9.8.0", "9.8.1",
            "9.9.0-alpha01", "9.9.0-beta01", "9.9.0-rc01", "9.9.0", "9.9.1",
            "9.10.0-alpha01", "9.10.0-beta01", "9.10.0-rc01", "9.10.0", "9.10.1",
            "9.11.0-alpha01", "9.11.0-beta01", "9.11.0-rc01",
            "9.12.0-alpha01", "9.12.0-beta01",
            "9.13.0-alpha01",
        ].shuffled()

        when:
        def selected = UpdateAgpVersions.selectVersionsFrom(gradleVersion, minimumSupported, allVersions)

        then:
        selected == selection

        where:
        minimumSupported            | selection
        null                        | ["9.7.1", "9.8.1", "9.9.1", "9.10.1", "9.11.0-rc01", "9.12.0-beta01", "9.13.0-alpha01"]
        VersionNumber.parse("9.10") | ["9.10.1", "9.11.0-rc01", "9.12.0-beta01", "9.13.0-alpha01"]
    }

    def "selects matching gradle major versions and latest major minors when no matching gradle major stable or rc available (minimumSupported=#minimumSupported)"() {
        given:
        def gradleVersion = GradleVersion.version("9.2")
        def allVersions = [
            "7.8.0",
            "8.9.0-alpha01", "8.9.0-beta01", "8.9.0-rc01", "8.9.0", "8.9.1",
            "8.10.0-alpha01", "8.10.0-beta01", "8.10.0-rc01", "8.10.0", "8.10.1",
            "8.11.0-alpha01", "8.11.0-beta01", "8.11.0-rc01",
            "8.12.0-alpha01", "8.12.0-beta01",
            "8.13.0-alpha01",
            "9.0.0-alpha11"
        ].shuffled()

        when:
        def selected = UpdateAgpVersions.selectVersionsFrom(gradleVersion, minimumSupported, allVersions)

        then:
        selected == selection

        where:
        minimumSupported            | selection
        null                        | ["8.10.1", "8.11.0-rc01", "8.12.0-beta01", "8.13.0-alpha01", "9.0.0-alpha11"]
        VersionNumber.parse("8.11") | ["8.11.0-rc01", "8.12.0-beta01", "8.13.0-alpha01", "9.0.0-alpha11"]
        VersionNumber.parse("8.9")  | ["8.9.1", "8.10.1", "8.11.0-rc01", "8.12.0-beta01", "8.13.0-alpha01", "9.0.0-alpha11"]
    }

    def "fail when minimumSupported higher than gradle major when matching gradle major stable or rc available"() {
        given:
        def gradleVersion = GradleVersion.version("9.2")
        def minimumSupported = VersionNumber.parse("8.9")
        def allVersions = ["8.9.0", "9.0.0"]

        when:
        UpdateAgpVersions.selectVersionsFrom(gradleVersion, minimumSupported, allVersions)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "minimumSupported must be at least 9.0.0, was 8.9.0"
    }

    def "fail when minimumSupported higher than gradle major when no matching gradle major stable or rc available"() {
        given:
        def gradleVersion = GradleVersion.version("9.2")
        def minimumSupported = VersionNumber.parse("7.9")
        def allVersions = ["8.9.0", "9.0.0-alpha11"]

        when:
        UpdateAgpVersions.selectVersionsFrom(gradleVersion, minimumSupported, allVersions)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "minimumSupported must be at least 8.0.0, was 7.9.0"
    }
}
