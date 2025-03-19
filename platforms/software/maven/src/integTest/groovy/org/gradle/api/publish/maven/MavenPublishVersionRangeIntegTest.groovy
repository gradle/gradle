/*
 * Copyright 2014 the original author or authors.
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

class MavenPublishVersionRangeIntegTest extends AbstractMavenPublishIntegTest {
    def mavenModule = javaLibrary(mavenRepo.module("org.gradle.test", "publishTest", "1.9"))

    void "version range is mapped to maven syntax in published pom file"() {
        given:
        settingsFile << "rootProject.name = 'publishTest' "
        buildFile << """
            apply plugin: 'maven-publish'
            apply plugin: 'java-library'

            group = 'org.gradle.test'
            version = '1.9'

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }

            tasks.compileJava {
                // Avoid resolving the classpath when caching the configuration
                classpath = files()
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
        mavenModule.assertPublished()
        mavenModule.parsedModuleMetadata.variant("apiElements") {
            dependency("group:projectA:latest.release")
            dependency("group:projectB:latest.integration")
            dependency("group:projectC:1.+")
            dependency("group:projectD:[1.0,2.0)")
            dependency("group:projectE:[1.0]")
            noMoreDependencies()
        }

        mavenModule.parsedPom.scopes.compile.assertDependsOn(
            "group:projectA:RELEASE",
            "group:projectB:LATEST",
            "group:projectC:1.+",
            "group:projectD:[1.0,2.0)",
            "group:projectE:1.0"
        )
    }
}
