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
import org.gradle.test.fixtures.maven.MavenDependencyExclusion

class MavenPublishJavaIntegTest extends AbstractMavenPublishIntegTest {
    def mavenModule = mavenRepo.module("org.gradle.test", "publishTest", "1.9")

    def "can publish jar and meta-data to maven repository"() {
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
        mavenModule.parsedPom.scopes.runtime.assertDependsOn("commons-collections:commons-collections:3.2.2", "commons-io:commons-io:1.4", "org.springframework:spring-core:2.5.6", "commons-beanutils:commons-beanutils:1.8.3", "commons-dbcp:commons-dbcp:1.4", "org.apache.camel:camel-jackson:2.15.3")
        assert mavenModule.parsedPom.scopes.runtime.hasDependencyExclusion("org.springframework:spring-core:2.5.6", new MavenDependencyExclusion("commons-logging", "commons-logging"))
        assert mavenModule.parsedPom.scopes.runtime.hasDependencyExclusion("commons-beanutils:commons-beanutils:1.8.3", new MavenDependencyExclusion("commons-logging", "*"))
        assert mavenModule.parsedPom.scopes.runtime.hasDependencyExclusion("commons-dbcp:commons-dbcp:1.4", new MavenDependencyExclusion("*", "*"))
        assert mavenModule.parsedPom.scopes.runtime.hasDependencyExclusion("org.apache.camel:camel-jackson:2.15.3", new MavenDependencyExclusion("*", "camel-core"))

        and:
        resolveArtifacts(mavenModule) == [
            "camel-jackson-2.15.3.jar", "commons-beanutils-1.8.3.jar", "commons-collections-3.2.2.jar", "commons-dbcp-1.4.jar", "commons-io-1.4.jar",
            "jackson-annotations-2.4.0.jar", "jackson-core-2.4.3.jar", "jackson-databind-2.4.3.jar", "jackson-module-jaxb-annotations-2.4.3.jar",
            "publishTest-1.9.jar", "spring-core-2.5.6.jar"]
    }

    def "can publish attached artifacts to maven repository"() {
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
        resolveArtifacts(mavenModule) == ["camel-jackson-2.15.3.jar", "commons-beanutils-1.8.3.jar", "commons-collections-3.2.2.jar", "commons-dbcp-1.4.jar", "commons-io-1.4.jar", "jackson-annotations-2.4.0.jar", "jackson-core-2.4.3.jar", "jackson-databind-2.4.3.jar", "jackson-module-jaxb-annotations-2.4.3.jar", "publishTest-1.9.jar", "spring-core-2.5.6.jar"]
        resolveArtifacts(mavenModule, [classifier: 'source']) == ["camel-jackson-2.15.3.jar", "commons-beanutils-1.8.3.jar", "commons-collections-3.2.2.jar", "commons-dbcp-1.4.jar", "commons-io-1.4.jar", "jackson-annotations-2.4.0.jar", "jackson-core-2.4.3.jar", "jackson-databind-2.4.3.jar", "jackson-module-jaxb-annotations-2.4.3.jar", "publishTest-1.9-source.jar", "publishTest-1.9.jar", "spring-core-2.5.6.jar"]
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
                compile "commons-collections:commons-collections:3.2.2"
                compileOnly "javax.servlet:servlet-api:2.5"
                runtime "commons-io:commons-io:1.4"
                testCompile "junit:junit:4.12"
                compile ("org.springframework:spring-core:2.5.6") {
                    exclude group: 'commons-logging', module: 'commons-logging'
                }
                compile ("commons-beanutils:commons-beanutils:1.8.3") {
                   exclude group : 'commons-logging'
                }
                compile ("commons-dbcp:commons-dbcp:1.4") {
                   transitive = false
                }
                compile ("org.apache.camel:camel-jackson:2.15.3") {
                   exclude module : 'camel-core'
                }
            }
"""

    }

}
