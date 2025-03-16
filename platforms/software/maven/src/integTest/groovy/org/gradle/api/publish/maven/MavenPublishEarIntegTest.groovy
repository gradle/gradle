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
package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest

class MavenPublishEarIntegTest extends AbstractMavenPublishIntegTest {
    void "can publish ear module"() {
        def earModule = mavenRepo.module("org.gradle.test", "publishEar", "1.9")

        given:
        settingsFile << "rootProject.name = 'publishEar' "

        and:
        buildFile << """
apply plugin: 'ear'
apply plugin: 'war'
apply plugin: 'maven-publish'

group = 'org.gradle.test'
version = '1.9'

${mavenCentralRepository()}

dependencies {
    implementation "commons-collections:commons-collections:3.2.2"
    runtimeOnly "commons-io:commons-io:1.4"
}

publishing {
    repositories {
        maven { url = "${mavenRepo.uri}" }
    }
    publications {
        mavenEar(MavenPublication) {
            artifact ear
        }
    }
}
"""

        when:
        succeeds 'assemble'

        then: "ear is built but not published"
        earModule.assertNotPublished()
        file('build/libs/publishEar-1.9.ear').assertExists()

        when:
        run "publish"

        then:
        earModule.assertPublishedAsEarModule()
        earModule.parsedPom.scopes.isEmpty()

        and:
        resolveArtifacts(earModule) {
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "publishEar-1.9.ear"
            }
        }
    }
}
