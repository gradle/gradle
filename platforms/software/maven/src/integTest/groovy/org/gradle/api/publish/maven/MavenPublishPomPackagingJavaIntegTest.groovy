/*
 * Copyright 2019 the original author or authors.
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

class MavenPublishPomPackagingJavaIntegTest extends AbstractMavenPublishIntegTest {
    def javaLibrary = javaLibrary(mavenRepo.module("org.gradle.test", "publishTest", "1.9"))

    def "can specify packaging for unknown packaging without changing artifact extension"() {
        given:
        createBuildScripts "foo"

        when:
        succeeds "publish"

        then:
        javaLibrary.assertPublished('foo', 'jar')
        javaLibrary.parsedModuleMetadata.variant("apiElements").files[0].name == "publishTest-1.9.jar"
        javaLibrary.parsedModuleMetadata.variant("apiElements").files[0].url == "publishTest-1.9.jar"
        javaLibrary.parsedModuleMetadata.variant("runtimeElements").files[0].name == "publishTest-1.9.jar"
        javaLibrary.parsedModuleMetadata.variant("runtimeElements").files[0].url == "publishTest-1.9.jar"
    }

    def "can specify packaging for known jar packaging without changing artifact extension"() {
        given:
        createBuildScripts "ejb"

        when:
        succeeds "publish"

        then:
        javaLibrary.assertPublished('ejb', 'jar')
        javaLibrary.parsedModuleMetadata.variant("apiElements").files[0].name == "publishTest-1.9.jar"
        javaLibrary.parsedModuleMetadata.variant("apiElements").files[0].url == "publishTest-1.9.jar"
        javaLibrary.parsedModuleMetadata.variant("runtimeElements").files[0].name == "publishTest-1.9.jar"
        javaLibrary.parsedModuleMetadata.variant("runtimeElements").files[0].url == "publishTest-1.9.jar"
    }

    def "can align packaging and artifact extension by changing both the main artifact and the packaging"() {
        given:
        createBuildScripts "foo"
        buildFile << """
            configurations {
                jar.archiveExtension.set('foo')
            }
        """

        when:
        succeeds "publish"

        then:
        javaLibrary.assertPublished('foo', 'foo')
        javaLibrary.parsedModuleMetadata.variant("apiElements").files[0].name == "publishTest-1.9.foo"
        javaLibrary.parsedModuleMetadata.variant("apiElements").files[0].url == "publishTest-1.9.foo"
        javaLibrary.parsedModuleMetadata.variant("runtimeElements").files[0].name == "publishTest-1.9.foo"
        javaLibrary.parsedModuleMetadata.variant("runtimeElements").files[0].url == "publishTest-1.9.foo"
    }

    def createBuildScripts(String packaging) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'java-library'
            apply plugin: 'maven-publish'

            group = 'org.gradle.test'
            version = '1.9'

            publishing {
                repositories {
                    maven { url = "${mavenRepo.uri}" }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                        pom.packaging = '$packaging'
                    }
                }
            }
"""
    }
}
