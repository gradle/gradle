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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore

class MavenPublishMultiProjectIntegTest extends AbstractIntegrationSpec {
    def project1module = mavenRepo.module("org.gradle.test", "project1", "1.9")

    def "project dependency correctly reflected in POM"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
    }
}
        """)

        when:
        run "publish"

        then:
        projectsCorrectlyPublished()
    }

    def "maven-publish plugin does not take archivesBaseName into account when publishing"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
    }
}

project(":project2") {
    archivesBaseName = "changed"
}
        """)

        when:
        run "publish"

        then:
        projectsCorrectlyPublished()
    }

    def "maven-publish plugin does not take mavenDeployer.pom.artifactId into account when publishing"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
    }
}

project(":project2") {
    apply plugin: 'maven'
    uploadArchives {
        repositories {
            mavenDeployer {
                repository(url: "file:///\$rootProject.projectDir/maven-repo")
                pom.artifactId = "changed"
            }
        }
    }
}
        """)

        when:
        run "publish"

        then:
        projectsCorrectlyPublished()
    }

    private def projectsCorrectlyPublished() {
        def project2 = mavenRepo.module("org.gradle.test", "project2", "1.9")
        project2.assertPublishedAsJavaModule()

        def project1pom = project1module.parsedPom
        project1pom.scopes.runtime.assertDependsOn("org.gradle.test", "project2", "1.9")

        return true
    }

    def "maven-publish plugin will use target project archivesBaseName for project dependency when target project does not have maven-publish plugin applied"() {
        given:
        settingsFile << """
include "project1", "project2"
        """

        buildFile << """
allprojects {
    group = "org.gradle.test"
    version = 1.9
}

project(":project1") {
    apply plugin: "java"
    apply plugin: "maven-publish"

    dependencies {
        compile project(":project2")
    }

    publishing {
        repositories {
            maven { url "file:///\$rootProject.projectDir/maven-repo" }
        }
        publications {
            add('maven', org.gradle.api.publish.maven.MavenPublication) {
                from components['java']
            }
        }
    }
}
project(":project2") {
    apply plugin: 'maven'
    archivesBaseName = "changed"
}
        """

        when:
        run "publish"

        then:

        project1module.assertPublishedAsJavaModule()
        project1module.parsedPom.scopes.runtime.assertDependsOn("org.gradle.test", "changed", "1.9")
    }

    @Ignore("This does not work: fix this as part of making the project coordinates customisable via DSL") // TODO:DAZ
    def "project dependency correctly reflected in POM if dependency publication pom is changed"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
    }
}

project(":project2") {
    publishing {
        publications {
            maven {
                pom.withXml {
                    asNode().artifactId[0].value = "changed"
                }
            }
        }
    }
}
        """)

        when:
        run ":project1:publish"

        then:
        def pom = project1module.parsedPom
        pom.scopes.runtime.assertDependsOn("org.gradle.test", "changed", "1.9")
    }

    def "multiple project dependencies correctly reflected in POMs"() {
        createBuildScripts("""
project(":project1") {
    dependencies {
        compile project(":project2")
        compile project(":project3")
    }
}

project(":project2") {
    dependencies {
        compile project(":project3")
    }
}
        """)

        when:
        run "publish"

        then:
        def pom = project1module.parsedPom
        pom.scopes.runtime.assertDependsOnArtifacts("project2", "project3")

        and:
        def pom2 = mavenRepo.module("org.gradle.test", "project2", "1.9").parsedPom
        pom2.scopes.runtime.assertDependsOnArtifacts("project3")

        and:
        def pom3 = mavenRepo.module("org.gradle.test", "project3", "1.9").parsedPom
        pom3.scopes.runtime == null
    }

    private void createBuildScripts(String append = "") {
        settingsFile << """
include "project1", "project2", "project3"
        """

        buildFile << """
allprojects {
    group = "org.gradle.test"
    version = 1.9
}

subprojects {
    apply plugin: "java"
    apply plugin: "maven-publish"

    publishing {
        repositories {
            maven { url "file:///\$rootProject.projectDir/maven-repo" }
        }
        publications {
            add('maven', org.gradle.api.publish.maven.MavenPublication) {
                from components['java']
            }
        }
    }
}

$append
        """
    }
}
