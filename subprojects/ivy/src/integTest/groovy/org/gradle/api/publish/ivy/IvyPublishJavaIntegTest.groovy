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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class IvyPublishJavaIntegTest extends AbstractIntegrationSpec {
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

        def ivy = ivyModule.ivy

        ivy.configurations.keySet() == ["default", "runtime"] as Set
        ivy.configurations["default"].extend == ["runtime"] as Set
        ivy.configurations["runtime"].extend == null

        ivy.artifacts["publishTest"].hasAttributes("jar", "jar", ["runtime"])

        ivy.dependencies["runtime"].assertDependsOn("commons-collections", "commons-collections", "3.2.1")
        ivy.dependencies["runtime"].assertDependsOn("commons-io", "commons-io", "1.4")
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
                            type "source"
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
        ivyModule.assertArtifactsPublished("publishTest-1.9.jar", "publishTest-source-1.9.jar", "ivy-1.9.xml")

        ivyModule.ivy.artifacts["publishTest-source"].hasAttributes("jar", "source", ["runtime"])
    }

    def createBuildScripts(def append) {
        settingsFile << "rootProject.name = 'publishTest' "

        buildFile << """
            apply plugin: 'ivy-publish'
            apply plugin: 'java'

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

            publishing {
                repositories {
                    ivy { url "${ivyRepo.uri}" }
                }
            }

$append
"""

    }
}
