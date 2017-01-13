/*
 * Copyright 2013 the original author or authors.
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

import spock.lang.Issue

class IvyPublishJavaIntegTest extends AbstractIvyPublishIntegTest {
    def ivyModule = ivyRepo.module("org.gradle.test", "publishTest", "1.9")

    public void "can publish jar and descriptor to ivy repository"() {
        given:
        createBuildScripts("""
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        ivyModule.assertPublishedAsJavaModule()

        with (ivyModule.parsedIvy) {
            configurations.keySet() == ["default", "compile", "runtime"] as Set
            configurations["default"].extend == ["runtime", "compile"] as Set
            configurations["runtime"].extend == null

            expectArtifact("publishTest").hasAttributes("jar", "jar", ["runtime"])
        }
        ivyModule.parsedIvy.assertDependsOn("commons-collections:commons-collections:3.2.2@runtime", "commons-io:commons-io:1.4@runtime")

        and:
        resolveArtifacts(ivyModule) == ["commons-collections-3.2.2.jar", "commons-io-1.4.jar", "publishTest-1.9.jar"]
    }

    public void "ignores extra artifacts added to configurations"() {
        given:
        createBuildScripts("""
            task extraJar(type: Jar) {
                from sourceSets.main.allJava
                baseName "publishTest-extra"
            }

            artifacts {
                runtime extraJar
                archives extraJar
                it."default" extraJar
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        ivyModule.assertPublishedAsJavaModule()
    }

    public void "can publish additional artifacts for java project"() {
        given:
        createBuildScripts("""
            task sourceJar(type: Jar) {
                from sourceSets.main.allJava
                baseName "publishTest-source"
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                        artifact(sourceJar) {
                            classifier "source"
                            type "sources"
                            conf "runtime"
                        }
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        ivyModule.assertPublished()
        ivyModule.assertArtifactsPublished("publishTest-1.9.jar", "publishTest-1.9-source.jar", "ivy-1.9.xml")

        ivyModule.parsedIvy.expectArtifact("publishTest", "jar", "source").hasAttributes("jar", "sources", ["runtime"], "source")

        and:
        resolveArtifacts(ivyModule) == ["commons-collections-3.2.2.jar", "commons-io-1.4.jar", "publishTest-1.9-source.jar", "publishTest-1.9.jar"]
    }

    @Issue("GRADLE-3514")
    public void "generated ivy descriptor includes dependency exclusions"() {
        given:
        createBuildScripts("""
            dependencies {
                compile 'org.springframework:spring-core:2.5.6', {
                    exclude group: 'commons-logging', module: 'commons-logging'
                }
                compile "commons-beanutils:commons-beanutils:1.8.3", {
                    exclude group: 'commons-logging'
                }
                compile "commons-dbcp:commons-dbcp:1.4", {
                    transitive = false
                }
                compile "org.apache.camel:camel-jackson:2.15.3", {
                    exclude module: 'camel-core'
                }
            }

            publishing {
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        ivyModule.assertPublishedAsJavaModule()

        def dependency = ivyModule.parsedIvy.expectDependency("org.springframework:spring-core:2.5.6")
        dependency.exclusions.size() == 1
        dependency.exclusions[0].org == 'commons-logging'
        dependency.exclusions[0].module == 'commons-logging'

        ivyModule.parsedIvy.dependencies["commons-beanutils:commons-beanutils:1.8.3"].hasConf("runtime->default")
        ivyModule.parsedIvy.dependencies["commons-beanutils:commons-beanutils:1.8.3"].exclusions[0].org == 'commons-logging'
        !ivyModule.parsedIvy.dependencies["commons-dbcp:commons-dbcp:1.4"].transitiveEnabled()
        ivyModule.parsedIvy.dependencies["org.apache.camel:camel-jackson:2.15.3"].hasConf("runtime->default")
        ivyModule.parsedIvy.dependencies["org.apache.camel:camel-jackson:2.15.3"].exclusions[0].module == 'camel-core'

        and:
        resolveArtifacts(ivyModule) == [
            "camel-jackson-2.15.3.jar",
            "commons-beanutils-1.8.3.jar",
            "commons-collections-3.2.2.jar",
            "commons-dbcp-1.4.jar",
            "commons-io-1.4.jar",
            "jackson-annotations-2.4.0.jar",
            "jackson-core-2.4.3.jar",
            "jackson-databind-2.4.3.jar",
            "jackson-module-jaxb-annotations-2.4.3.jar",
            "publishTest-1.9.jar",
            "spring-core-2.5.6.jar"
        ]
    }

    def createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
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
            }
"""
    }
}
