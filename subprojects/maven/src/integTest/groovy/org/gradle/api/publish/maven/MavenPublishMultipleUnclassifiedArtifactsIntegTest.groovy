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

class MavenPublishMultipleUnclassifiedArtifactsIntegTest extends AbstractMavenPublishIntegTest {
    def mavenModule = mavenRepo.module("org.gradle.test", "publishTest", "1.9")

    public void "can publish 2 'unclassified' artifacts with packaging specified"() {
        given:
        createBuildScriptsWithTwoUnclassifiedArtifacts('pom.packaging "zip"')

        when:
        run "publish"

        then:
        mavenModule.assertPublished()
        mavenModule.assertArtifactsPublished("publishTest-1.9.jar", "publishTest-1.9.pom", "publishTest-1.9.zip")
        mavenModule.parsedPom.packaging == 'zip'
    }

    public void "publish 2 'unclassified' artifacts with no packaging specified: validation fails."() {
        given:
        createBuildScriptsWithTwoUnclassifiedArtifacts('')

        when:
        fails "publish"

        then:
        failure.assertHasCause('Invalid publication \'maven\': Cannot determine main artifact - multiple artifacts found with empty classifier.')
    }

    def createBuildScriptsWithTwoUnclassifiedArtifacts(publicationOptions) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            task sourceZip(type: Jar) {
                from sourceSets.main.allJava
                extension 'zip'
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        artifact sourceZip
$publicationOptions
                    }
                }
            }

            group = 'org.gradle.test'
            version = '1.9'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile "commons-collections:commons-collections:3.2.1"
                runtime "commons-io:commons-io:1.4"
                testCompile "junit:junit:4.11"
            }
"""

    }
}
