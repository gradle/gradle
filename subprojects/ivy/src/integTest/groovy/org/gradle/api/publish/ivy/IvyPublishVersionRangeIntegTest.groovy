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

import groovy.transform.NotYetImplemented
import spock.lang.Issue

class IvyPublishVersionRangeIntegTest extends AbstractIvyPublishIntegTest {
    def ivyModule = ivyRepo.module("org.gradle.test", "publishTest", "1.9")

    public void "version range is mapped to ivy syntax in published ivy descriptor file"() {
        given:
        settingsFile << "rootProject.name = 'publishTest' "
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'

            group = 'org.gradle.test'
            version = '1.9'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            dependencies {
                compile "group:projectA:latest.release"
                compile "group:projectB:latest.integration"
                compile "group:projectC:1.+"
                compile "group:projectD:[1.0,2.0)"
                compile "group:projectE:[1.0]"
            }"""

        when:
        run "publish"

        then:
        ivyModule.assertPublishedAsJavaModule()
        ivyModule.parsedIvy.assertDependsOn(
            "group:projectA:latest.release@runtime",
            "group:projectB:latest.integration@runtime",
            "group:projectC:1.+@runtime",
            "group:projectD:[1.0,2.0)@runtime",
            "group:projectE:[1.0]@runtime"
        )
    }

    @Issue("GRADLE-3339")
    @NotYetImplemented
    def "publishes ivy dependency for Gradle dependency with empty version"() {
        settingsFile << "rootProject.name = 'publishTest' "
        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'

            group = 'org.gradle.test'
            version = '1.9'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }

            dependencies {
                compile "group:projectA"
                compile group:"group", name:"projectB", version:null
            }
        """

        when:
        run "publish"

        then:
        ivyModule.assertPublishedAsJavaModule()
        ivyModule.parsedIvy.assertDependsOn("group:projectA:", "group:projectB:")
    }

}