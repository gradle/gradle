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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import org.gradle.test.fixtures.maven.MavenLocalRepository
import org.gradle.util.SetSystemProperties
import org.junit.Rule
/**
 * Tests “simple” maven publishing scenarios
 */
class MavenPublishSnapshotIntegTest extends AbstractMavenPublishIntegTest {
    @Rule
    SetSystemProperties sysProp = new SetSystemProperties()

    MavenLocalRepository localM2Repo

    def "setup"() {
        localM2Repo = m2.mavenRepo()
    }

    def "can publish a snapshot version"() {
        settingsFile << 'rootProject.name = "snapshotPublish"'
        buildFile << """
    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = 'org.gradle'
    version = '1.0-SNAPSHOT'

    publishing {
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        publications {
            pub(MavenPublication) {
                from components.java
            }
        }
    }
"""

        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module('org.gradle', 'snapshotPublish', '1.0-SNAPSHOT')
        module.assertArtifactsPublished("snapshotPublish-${module.publishArtifactVersion}.module", "snapshotPublish-${module.publishArtifactVersion}.jar", "snapshotPublish-${module.publishArtifactVersion}.pom", "maven-metadata.xml")

        and:
        module.parsedPom.version == '1.0-SNAPSHOT'

        with (module.rootMetaData) {
            groupId == "org.gradle"
            artifactId == "snapshotPublish"
            releaseVersion == null
            versions == ['1.0-SNAPSHOT']
        }

        with (module.snapshotMetaData) {
            groupId == "org.gradle"
            artifactId == "snapshotPublish"
            version == "1.0-SNAPSHOT"

            snapshotTimestamp != null
            snapshotBuildNumber == '1'
            lastUpdated == snapshotTimestamp.replace('.', '')

            snapshotVersions == ["1.0-${snapshotTimestamp}-${snapshotBuildNumber}"]
        }

        and:
        resolveArtifacts(module) == ["snapshotPublish-${module.publishArtifactVersion}.jar"]
    }
}
