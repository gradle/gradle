/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.util

import org.apache.ivy.Ivy
import org.apache.tools.ant.Main
import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class GradleVersionTest extends Specification {
    final GradleVersion version = GradleVersion.current()

    def "valid versions"() {
        expect:
        version.valid
        !GradleVersion.version("asdfasdfas").valid
        GradleVersion.version("1.0").valid
    }

    def currentVersionHasNonNullVersion() {
        expect:
        version.version
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-1892")
    def "build time should always print in UTC"() {
        expect:
        version.buildTime.endsWith("UTC")
    }

    def equalsAndHashCode() {
        expect:
        Matchers.strictlyEquals(GradleVersion.version('0.9'), GradleVersion.version('0.9'))
        GradleVersion.version('0.9') != GradleVersion.version('1.0')
    }

    def canConstructVersionFromString(String version) {
        expect:
        def gradleVersion = GradleVersion.version(version)
        gradleVersion.version == version
        gradleVersion.toString() == "Gradle ${version}"

        where:
        version << [
                '1.0',
                '12.4.5.67',
                '1.0-milestone-5',
                '1.0-milestone-5a',
                '3.2-rc-2',
        ]
    }

    def versionsWithTimestampAreConsideredSnapshots(String version) {
        expect:
        def gradleVersion = GradleVersion.version(version)
        gradleVersion.version == version
        gradleVersion.snapshot

        where:
        version << [
                '0.9-20101220110000+1100',
                '0.9-20101220110000-0800',
                '1.2-20120501110000'
        ]
    }

    def versionsWithoutTimestampAreNotConsideredSnapshots(String version) {
        expect:
        !GradleVersion.version(version).snapshot

        where:
        version << [
                '0.9-milestone-5',
                '2.1-rc-1',
                '1.2',
                '1.2.1']
    }

    def canOnlyQueryVersionStringForUnrecognizedVersion(String version) {
        def gradleVersion = GradleVersion.version(version)

        expect:
        gradleVersion.version == version
        gradleVersion.snapshot

        when:
        gradleVersion > GradleVersion.version('1.2')

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot compare unrecognized Gradle version '${version}'."

        where:
        version << [
                'abc',
                '3.0-status-5-master',
                'user-master',
        ]
    }

    def canCompareMajorVersions() {
        expect:
        GradleVersion.version(a) > GradleVersion.version(b)
        GradleVersion.version(b) < GradleVersion.version(a)
        GradleVersion.version(a) == GradleVersion.version(a)
        GradleVersion.version(b) == GradleVersion.version(b)

        where:
        a      | b
        '0.9'  | '0.8'
        '1.0'  | '0.10'
        '10.0' | '2.1'
        '2.5'  | '2.4'
    }

    def canComparePointVersions() {
        expect:
        GradleVersion.version(a) > GradleVersion.version(b)
        GradleVersion.version(b) < GradleVersion.version(a)
        GradleVersion.version(a) == GradleVersion.version(a)
        GradleVersion.version(b) == GradleVersion.version(b)

        where:
        a                   | b
        '0.9.2'             | '0.9.1'
        '0.10.1'            | '0.9.2'
        '1.2.3.40'          | '1.2.3.8'
        '1.2.3.1'           | '1.2.3'
        '1.2.3.1.4.12.9023' | '1.2.3'
    }

    def canComparePointVersionAndMajorVersions() {
        expect:
        GradleVersion.version(a) > GradleVersion.version(b)
        GradleVersion.version(b) < GradleVersion.version(a)
        GradleVersion.version(a) == GradleVersion.version(a)
        GradleVersion.version(b) == GradleVersion.version(b)

        where:
        a       | b
        '0.9.1' | '0.9'
        '0.10'  | '0.9.1'
    }

    def canComparePreviewsMilestonesAndRCVersions() {
        expect:
        GradleVersion.version(a) > GradleVersion.version(b)
        GradleVersion.version(b) < GradleVersion.version(a)
        GradleVersion.version(a) == GradleVersion.version(a)
        GradleVersion.version(b) == GradleVersion.version(b)

        where:
        a                 | b
        '1.0-milestone-2' | '1.0-milestone-1'
        '1.0-preview-2'   | '1.0-preview-1'
        '1.0-rc-2'        | '1.0-rc-1'
        '1.0-preview-1'   | '1.0-milestone-7'
        '1.0-rc-7'        | '1.0-rc-1'
        '1.0'             | '1.0-rc-7'
    }

    def canComparePatchVersion() {
        expect:
        GradleVersion.version(a) > GradleVersion.version(b)
        GradleVersion.version(b) < GradleVersion.version(a)
        GradleVersion.version(a) == GradleVersion.version(a)
        GradleVersion.version(b) == GradleVersion.version(b)

        where:
        a                  | b
        '1.0-milestone-2a' | '1.0-milestone-2'
        '1.0-milestone-2b' | '1.0-milestone-2a'
        '1.0-milestone-3'  | '1.0-milestone-2b'
        '1.0'              | '1.0-milestone-2b'
    }

    def canCompareSnapshotVersions() {
        expect:
        GradleVersion.version(a) > GradleVersion.version(b)
        GradleVersion.version(b) < GradleVersion.version(a)
        GradleVersion.version(a) == GradleVersion.version(a)
        GradleVersion.version(b) == GradleVersion.version(b)

        where:
        a                         | b
        '0.9-20101220110000+1100' | '0.9-20101220100000+1100'
        '0.9-20101220110000+1000' | '0.9-20101220100000+1100'
        '0.9-20101220110000-0100' | '0.9-20101220100000+0000'
        '0.9-20101220110000'      | '0.9-20101220100000'
        '0.9-20101220110000'      | '0.9-20101220110000+0100'
        '0.9-20101220110000-0100' | '0.9-20101220110000'
        '0.9'                     | '0.9-20101220100000+1000'
        '0.9'                     | '0.9-20101220100000'
    }

    def "can get version base"() {
        expect:
        GradleVersion.version(v).versionBase == base

        where:
        v                         | base
        "1.0"                     | "1.0"
        "1.0-rc-1"                | "1.0"
        '0.9-20101220100000+1000' | "0.9"
        '0.9-20101220100000'      | "0.9"
        "asdfasd"                 | null
    }

    def "can get version major"() {
        expect:
        GradleVersion.version(v).major == major

        where:
        v                         | major
        "1.0"                     | 1
        "1.0-rc-1"                | 1
        '0.9-20101220100000+1000' | 0
        '0.9-20101220100000'      | 0
        "asdfasd"                 | -1
    }

    def prettyPrint() {
        String expectedText = """
------------------------------------------------------------
Gradle $version.version
------------------------------------------------------------

Build time:   $version.buildTime
Build number: $version.buildNumber
Revision:     $version.commitId

Groovy:       $InvokerHelper.version
Ant:          $Main.antVersion
Ivy:          ${Ivy.ivyVersion}
JVM:          ${Jvm.current()}
OS:           ${OperatingSystem.current()}
"""
        expect:
        version.prettyPrint() == expectedText
    }
}
