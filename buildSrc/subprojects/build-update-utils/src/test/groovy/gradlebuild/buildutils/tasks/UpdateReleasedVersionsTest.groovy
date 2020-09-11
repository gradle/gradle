/*
 * Copyright 2020 the original author or authors.
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

import gradlebuild.buildutils.model.ReleasedVersion
import gradlebuild.buildutils.model.ReleasedVersions
import spock.lang.Specification

import java.text.SimpleDateFormat

import static java.util.concurrent.TimeUnit.DAYS

class UpdateReleasedVersionsTest extends Specification {

    def format = new SimpleDateFormat('yyyyMMddHHmmssZ')

    def setup() {
        format.timeZone = TimeZone.getTimeZone("UTC")
    }

    def "final release is added to list"() {
        def snapshot = snapshot('4.3')
        def rc = releasedVersion('4.3-rc-1')
        def versions = releasedVersions(snapshot, rc, [])
        def version = new ReleasedVersion('4.2', '20170913122310+0000')

        expect:
        UpdateReleasedVersions.@Companion.updateReleasedVersions(version, versions) == releasedVersions(snapshot, rc, [version])
    }

    def "final releases are sorted by version"() {
        def snapshot = snapshot('4.3')
        def rc = releasedVersion('4.3-rc-1')
        def referenceBuildTime = System.currentTimeMillis() - DAYS.toMillis(10)
        def finalVersionsBefore = [
            releasedVersion('4.3.1', referenceBuildTime + 7),
            releasedVersion('4.4', referenceBuildTime + 5),
            releasedVersion('4.3', referenceBuildTime + 2),
            releasedVersion('4.2.1', referenceBuildTime + 3),
            releasedVersion('4.2', referenceBuildTime),
        ]
        def versions = releasedVersions(snapshot, rc, finalVersionsBefore)
        def version = new ReleasedVersion('4.2', '20170913122310+0000')
        expect:
        def expectedVersions = (finalVersionsBefore + version).sort { it.version }.reverse()
        UpdateReleasedVersions.@Companion.updateReleasedVersions(version, versions) == releasedVersions(snapshot, rc, expectedVersions)
    }

    def "newer snapshots are stored"() {
        def referenceBuildTime = System.currentTimeMillis() - DAYS.toMillis(10)
        def oldSnapshot = snapshot('4.3', referenceBuildTime)
        def rc = releasedVersion('4.2-rc-1')
        def versions = releasedVersions(oldSnapshot, rc, [])

        def newSnapshot = snapshot(version, referenceBuildTime + buildTime)
        expect:
        UpdateReleasedVersions.@Companion.updateReleasedVersions(newSnapshot, versions) == releasedVersions(newSnapshot, rc, [])

        where:
        version | buildTime
        '4.3'   | 1
        '4.4'   | -1
        '4.4'   | 1
    }

    def "older snapshots are not stored"() {
        def referenceBuildTime = System.currentTimeMillis() - DAYS.toMillis(10)
        def oldSnapshot = snapshot('4.3', referenceBuildTime)
        def rc = releasedVersion('4.2-rc-1')
        def versions = releasedVersions(oldSnapshot, rc, [])

        def newSnapshot = snapshot(version, referenceBuildTime + buildTime)
        expect:
        UpdateReleasedVersions.@Companion.updateReleasedVersions(newSnapshot, versions) == releasedVersions(oldSnapshot, rc, [])

        where:
        version | buildTime
        '4.3'   | -1
        '4.2'   | 1
        '4.2'   | -1
    }

    def "newer rcs are stored"() {
        def referenceBuildTime = System.currentTimeMillis() - DAYS.toMillis(10)
        def oldRc = releasedVersion('4.3-rc-2', referenceBuildTime)
        def snapshotVersion = snapshot('4.3', referenceBuildTime)
        def versions = releasedVersions(snapshotVersion, oldRc, [])

        def newRc = releasedVersion(version, referenceBuildTime + buildTime)
        expect:
        UpdateReleasedVersions.@Companion.updateReleasedVersions(newRc, versions) == releasedVersions(snapshotVersion, newRc, [])

        where:
        version | buildTime
        '4.3-rc-3'   | 0
        '4.4-rc-1'   | -1
        '4.3-rc-4'   | 1
    }

    def "older rcs are not stored"() {
        def referenceBuildTime = System.currentTimeMillis() - DAYS.toMillis(10)
        def oldRc = releasedVersion('4.3-rc-2', referenceBuildTime)
        def snapshotVersion = snapshot('4.3', referenceBuildTime)
        def versions = releasedVersions(snapshotVersion, oldRc, [])

        def newRc = releasedVersion(version, referenceBuildTime + buildTime)
        expect:
        UpdateReleasedVersions.@Companion.updateReleasedVersions(newRc, versions) == releasedVersions(snapshotVersion, oldRc, [])

        where:
        version | buildTime
        '4.3-rc-1'   | 1
        '4.2-rc-3'   | -1
        '4.2-rc-3'   | 1
    }

    ReleasedVersions releasedVersions(ReleasedVersion snapshot, ReleasedVersion rc, List<ReleasedVersion> versions) {
        new ReleasedVersions(snapshot, rc, versions)
    }

    ReleasedVersion releasedVersion(String version, long date = System.currentTimeMillis()) {
        new ReleasedVersion(version, format.format(new Date(date)))
    }

    ReleasedVersion snapshot(String baseVersion, long date = System.currentTimeMillis()) {
        releasedVersion("${baseVersion}-${format.format(new Date(date))}", date)
    }
}
