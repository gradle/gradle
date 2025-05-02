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

package org.gradle.api.publish.ivy

import groovy.test.NotYetImplemented
import spock.lang.Issue

class IvyPublishVersionRangeIntegTest extends AbstractIvyPublishIntegTest {
    def ivyModule = javaLibrary(ivyRepo.module("org.gradle.test", "publishTest", "1.9"))

    void "version range is mapped to ivy syntax in published ivy descriptor file"() {
        given:
        settingsFile << "rootProject.name = 'publishTest' "
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java-library'

            group = 'org.gradle.test'
            version = '1.9'

            ${emptyJavaClasspath()}

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            dependencies {
                api "group:projectA:latest.release"
                api "group:projectB:latest.integration"
                api "group:projectC:1.+"
                api "group:projectD:[1.0,2.0)"
                api "group:projectE:[1.0]"
            }"""

        when:
        run "publish"

        then:
        ivyModule.assertPublishedAsJavaModule()
        ivyModule.assertApiDependencies(
            "group:projectA:latest.release",
            "group:projectB:latest.integration",
            "group:projectC:1.+",
            "group:projectD:[1.0,2.0)",
            "group:projectE:[1.0]"
        )
    }

    @Issue("GRADLE-3339")
    @NotYetImplemented
    def "publishes ivy dependency for Gradle dependency with empty version"() {
        settingsFile << "rootProject.name = 'publishTest' "
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java-library'

            group = 'org.gradle.test'
            version = '1.9'

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            dependencies {
                api "group:projectA"
                api group:"group", name:"projectB", version:null
            }
        """

        when:
        run "publish"

        then:
        ivyModule.assertPublishedAsJavaModule()
        ivyModule.parsedIvy.assertDependsOn("group:projectA:", "group:projectB:")
    }

}
