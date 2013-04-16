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

class MavenPublishJavaIntegTest extends AbstractMavenPublishIntegTest {
    def mavenModule = mavenRepo.module("org.gradle.test", "publishTest", "1.9")

    public void "can publish jar and meta-data to maven repository"() {
        given:
        createBuildScripts("""
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        mavenModule.assertPublishedAsJavaModule()

        mavenModule.parsedPom.scopes.keySet() == ["runtime"] as Set
        mavenModule.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:3.2.1", "commons-io:commons-io:1.4")

        and:
        resolveArtifacts(mavenModule) == ["commons-collections-3.2.1.jar", "commons-io-1.4.jar", "publishTest-1.9.jar"]
    }

    public void "can publish attached artifacts to maven repository"() {
        given:
        createBuildScripts("""
            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                classifier "source"
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                        artifact sourceJar
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        mavenModule.assertPublished()
        mavenModule.assertArtifactsPublished("publishTest-1.9.jar", "publishTest-1.9.pom", "publishTest-1.9-source.jar")

        and:
        resolveArtifacts(mavenModule) == ["commons-collections-3.2.1.jar", "commons-io-1.4.jar", "publishTest-1.9.jar"]
        resolveArtifacts(mavenModule, [classifier: 'source']) == ["commons-collections-3.2.1.jar", "commons-io-1.4.jar", "publishTest-1.9-source.jar", "publishTest-1.9.jar"]
    }

    def createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java'

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

$append

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
