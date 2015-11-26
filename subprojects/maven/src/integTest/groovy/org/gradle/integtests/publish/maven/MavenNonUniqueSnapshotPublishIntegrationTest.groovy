/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.integtests.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class MavenNonUniqueSnapshotPublishIntegrationTest extends AbstractIntegrationSpec {
    public void "can publish a non-unique snapshot version"() {
        given:
        file("settings.gradle") << "rootProject.name = 'publishTest' "

        and:
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'

group = 'org.gradle.test'
version = '4.2-SNAPSHOT'

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri("${mavenRepo.uri}"))
            uniqueVersion = false
        }
    }
}
"""

        when:
        run "uploadArchives"

        then:
        def module = mavenRepo.module("org.gradle.test", "publishTest", "4.2-SNAPSHOT")
        module.withNonUniqueSnapshots()

        module.assertArtifactsPublished("publishTest-4.2-SNAPSHOT.pom", "publishTest-4.2-SNAPSHOT.jar", "maven-metadata.xml")
        module.getParsedPom().version == '4.2-SNAPSHOT'
    }
}
