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
/**
 * Tests publishing of maven snapshots
 */
class MavenPublishSnapshotIntegTest extends AbstractMavenPublishIntegTest {
    def "can publish snapshot versions"() {
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
        def module = mavenRepo.module('org.gradle', 'snapshotPublish', '1.0-SNAPSHOT')

        when:
        succeeds 'publish'

        then:
        def initialVersion = module.publishArtifactVersion
        def initialArtifacts = ["snapshotPublish-${initialVersion}.module", "snapshotPublish-${initialVersion}.jar", "snapshotPublish-${initialVersion}.pom"]
        module.assertArtifactsPublished(initialArtifacts + ["maven-metadata.xml"])

        and:
        module.parsedPom.version == '1.0-SNAPSHOT'

        with (module.rootMetaData) {
            groupId == "org.gradle"
            artifactId == "snapshotPublish"
            releaseVersion == null
            latestVersion == '1.0-SNAPSHOT'
            versions == ['1.0-SNAPSHOT']
        }

        with (module.snapshotMetaData) {
            groupId == "org.gradle"
            artifactId == "snapshotPublish"
            version == "1.0-SNAPSHOT"

            snapshotTimestamp != null
            snapshotBuildNumber == '1'
            localSnapshot == false
            lastUpdated == snapshotTimestamp.replace('.', '')

            snapshotVersions == ["1.0-${snapshotTimestamp}-${snapshotBuildNumber}"]
        }

        when: // Publish a second time
        succeeds 'publish'

        then:
        def secondVersion = module.publishArtifactVersion
        List<String> secondArtifacts = ["snapshotPublish-${secondVersion}.module", "snapshotPublish-${secondVersion}.jar", "snapshotPublish-${secondVersion}.pom"]
        module.assertArtifactsPublished(["maven-metadata.xml"] + initialArtifacts + secondArtifacts)

        and:
        module.parsedPom.version == '1.0-SNAPSHOT'
        module.snapshotMetaData.snapshotBuildNumber == '2'

        module.snapshotMetaData.snapshotVersions == [secondVersion]

        and:
        resolveArtifacts(module) { expectFiles "snapshotPublish-${secondVersion}.jar" }
    }

    def "can publish a snapshot version that was previously published with uploadArchives"() {
        given:
        using m2 // uploadArchives writes to .m2/repo

        and:
        settingsFile << 'rootProject.name = "snapshotPublish"'
        buildFile << """
    apply plugin: 'java'
    apply plugin: 'maven'
    apply plugin: 'maven-publish'

    group = 'org.gradle'
    version = '1.0-SNAPSHOT'

    uploadArchives {
        repositories {
            mavenDeployer {
                snapshotRepository(url: "${mavenRepo.uri}")
            }
        }
    }

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
        def module = mavenRepo.module('org.gradle', 'snapshotPublish', '1.0-SNAPSHOT')

        when:
        succeeds 'uploadArchives'

        then:
        List<String>  initialArtifacts = ["snapshotPublish-${module.publishArtifactVersion}.jar", "snapshotPublish-${module.publishArtifactVersion}.pom"]
        module.assertArtifactsPublished(["maven-metadata.xml"] + initialArtifacts)

        and:
        module.parsedPom.version == '1.0-SNAPSHOT'
        module.snapshotMetaData.snapshotBuildNumber == '1'

        when:
        succeeds 'publish'

        then:
        def secondVersion = module.publishArtifactVersion
        List<String> secondArtifacts = ["snapshotPublish-${secondVersion}.module", "snapshotPublish-${secondVersion}.jar", "snapshotPublish-${secondVersion}.pom"]
        module.assertArtifactsPublished(["maven-metadata.xml"] + initialArtifacts + secondArtifacts)

        and:
        module.parsedPom.version == '1.0-SNAPSHOT'
        module.snapshotMetaData.snapshotBuildNumber == '2'
        module.snapshotMetaData.snapshotVersions == [secondVersion]

        and:
        resolveArtifacts(module) { expectFiles "snapshotPublish-${secondVersion}.jar" }
    }

    def "can install snapshot versions"() {
        using m2

        settingsFile << 'rootProject.name = "snapshotInstall"'
        buildFile << """
    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = 'org.gradle'
    version = '1.0-SNAPSHOT'

    publishing {
        publications {
            pub(MavenPublication) {
                from components.java
            }
        }
    }
"""
        def module = getM2().mavenRepo().module('org.gradle', 'snapshotInstall', '1.0-SNAPSHOT')

        when:
        succeeds 'publishToMavenLocal'

        then:
        module.assertArtifactsPublished("maven-metadata-local.xml", "snapshotInstall-1.0-SNAPSHOT.module", "snapshotInstall-1.0-SNAPSHOT.jar", "snapshotInstall-1.0-SNAPSHOT.pom")

        and:
        module.parsedPom.version == '1.0-SNAPSHOT'

        with (module.rootMetaData) {
            groupId == "org.gradle"
            artifactId == "snapshotInstall"
            releaseVersion == null
            latestVersion == '1.0-SNAPSHOT'
            versions == ['1.0-SNAPSHOT']
        }

        with (module.snapshotMetaData) {
            groupId == "org.gradle"
            artifactId == "snapshotInstall"
            version == "1.0-SNAPSHOT"

            snapshotTimestamp == null
            snapshotBuildNumber == null
            localSnapshot == true

            lastUpdated != null

            snapshotVersions == ["1.0-SNAPSHOT"]
        }

        // Install a second time
        when:
        succeeds 'publishToMavenLocal'

        then:
        module.assertArtifactsPublished("maven-metadata-local.xml", "snapshotInstall-1.0-SNAPSHOT.module", "snapshotInstall-1.0-SNAPSHOT.jar", "snapshotInstall-1.0-SNAPSHOT.pom")

        and:
        module.parsedPom.version == '1.0-SNAPSHOT'

        with (module.rootMetaData) {
            groupId == "org.gradle"
            artifactId == "snapshotInstall"
            releaseVersion == null
            latestVersion == '1.0-SNAPSHOT'
            versions == ['1.0-SNAPSHOT']
        }

        with (module.snapshotMetaData) {
            groupId == "org.gradle"
            artifactId == "snapshotInstall"
            version == "1.0-SNAPSHOT"

            snapshotTimestamp == null
            snapshotBuildNumber == null
            localSnapshot == true

            lastUpdated != null

            snapshotVersions == ["1.0-SNAPSHOT"]
        }
    }
}
