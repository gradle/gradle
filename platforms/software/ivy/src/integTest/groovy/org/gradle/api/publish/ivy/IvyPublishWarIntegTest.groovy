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
package org.gradle.api.publish.ivy

class IvyPublishWarIntegTest extends AbstractIvyPublishIntegTest {

    void "can publish WAR only for mixed java and WAR project"() {
        given:
        file("settings.gradle") << "rootProject.name = 'publishTest' "

        and:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'war'
            apply plugin: 'ivy-publish'

            group = 'org.gradle.test'
            version = '1.9'

            ${mavenCentralRepository()}

            dependencies {
                implementation "commons-collections:commons-collections:3.2.2"
                runtimeOnly "commons-io:commons-io:1.4"
                providedCompile "commons-lang:commons-lang:2.6"
                providedRuntime "commons-cli:commons-cli:1.2"
                testImplementation "junit:junit:4.13"
            }

            publishing {
                repositories {
                    ivy {
                        url '${ivyRepo.uri}'
                    }
                }
                publications {
                    ivyWeb(IvyPublication) {
                        from components.web
                    }
                }
            }
        """

        when:
        run "publish"

        then: "module is published with artifacts"
        def ivyModule = javaLibrary(ivyRepo.module("org.gradle.test", "publishTest", "1.9"))
        ivyModule.assertPublishedAsWebModule()

        and: "correct configurations and dependencies declared"
        with (ivyModule.parsedIvy) {
            configurations.keySet() == ["default", "master"] as Set
            configurations.default.extend == ["master"] as Set
            configurations.master.extend == null

            dependencies.isEmpty()
        }

        and: "can resolve warfile"
        resolveArtifacts(ivyModule) { expectFiles "publishTest-1.9.war" }
    }
}
