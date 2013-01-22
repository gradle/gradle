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
        ivyModule.assertArtifactsPublished("ivy-1.9.xml", "publishTest-1.9.jar")
        // TODO:DAZ check configurations and artifacts in ivy.xml
        ivyModule.ivy.dependencies.runtime.assertDependsOn("commons-collections", "commons-collections", "3.2.1")
        ivyModule.ivy.dependencies.runtime.assertDependsOn("commons-io", "commons-io", "1.4")
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
                        artifact sourceJar
                    }
                }
            }
""")

        when:
        run "publish"

        then:
        ivyModule.assertPublished()
        ivyModule.assertArtifactsPublished("publishTest-1.9.jar", "publishTest-source-1.9.jar", "ivy-1.9.xml")
        with(ivyModule.ivy.artifacts."publishTest-source") {
            name == "publishTest-source"
            ext == "jar"
            "runtime" in conf
        }
        // TODO Check artifacts in ivy.xml
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
