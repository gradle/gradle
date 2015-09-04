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

class MavenPublishMultiProjectIntegTest extends AbstractMavenPublishIntegTest {
    def project1 = mavenRepo.module("org.gradle.test", "project1", "1.0")
    def project2 = mavenRepo.module("org.gradle.test", "project2", "2.0")
    def project3 = mavenRepo.module("org.gradle.test", "project3", "3.0")

    def "project dependency correctly reflected in POM"() {
        createBuildScripts()

        when:
        run "publish"

        then:
        projectsCorrectlyPublished()
    }

    def "project dependencies reference publication identity of dependent project"() {
        def project3 = mavenRepo.module("changed.group", "changed-artifact-id", "changed")

        createBuildScripts("""
project(":project3") {
    publishing {
        publications.maven {
            groupId "changed.group"
            artifactId "changed-artifact-id"
            version "changed"
        }
    }
}
""")

        when:
        run "publish"

        then:
        project1.assertPublishedAsJavaModule()
        project1.parsedPom.scopes.runtime.assertDependsOn("changed.group:changed-artifact-id:changed", "org.gradle.test:project2:2.0")

        project2.assertPublishedAsJavaModule()
        project2.parsedPom.scopes.runtime.assertDependsOn("changed.group:changed-artifact-id:changed")

        project3.assertPublishedAsJavaModule()
        project3.parsedPom.scopes.runtime == null

        and:
        resolveArtifacts(project1) == ['changed-artifact-id-changed.jar', 'project1-1.0.jar', 'project2-2.0.jar']
    }

    def "reports failure when project dependency references a project with multiple publications"() {
        createBuildScripts("""
project(":project3") {
    publishing {
        publications {
            extraMaven(MavenPublication) {
                from components.java
                groupId "extra.group"
                artifactId "extra-artifact"
                version "extra"
            }
        }
    }
}
""")

        when:
        fails "publish"

        then:
        failure.assertHasCause "Exception thrown while executing model rule: PublishingPlugin.Rules#publishing"
        failure.assertHasCause "Publishing is not yet able to resolve a dependency on a project with multiple different publications."
    }

    def "maven-publish plugin does not take archivesBaseName into account when publishing"() {
        createBuildScripts("""
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
project(":project2") {
    apply plugin: 'maven'
    uploadArchives {
        repositories {
            mavenDeployer {
                repository(url: "${mavenRepo.uri}")
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
        project1.assertPublishedAsJavaModule()
        project1.parsedPom.scopes.runtime.assertDependsOn("org.gradle.test:project2:2.0", "org.gradle.test:project3:3.0")

        project2.assertPublishedAsJavaModule()
        project2.parsedPom.scopes.runtime.assertDependsOn("org.gradle.test:project3:3.0")

        project3.assertPublishedAsJavaModule()
        project3.parsedPom.scopes == null

        resolveArtifacts(project1) == ["project1-1.0.jar", "project2-2.0.jar", "project3-3.0.jar"]

        return true
    }

    def "maven-publish plugin uses target project name for project dependency when target project does not have maven-publish plugin applied"() {
        given:
        settingsFile << """
include "project1", "project2"
        """

        buildFile << """
allprojects {
    group = "org.gradle.test"
}

project(":project1") {
    apply plugin: "java"
    apply plugin: "maven-publish"

    version = "1.0"

    dependencies {
        compile project(":project2")
    }

    publishing {
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        publications {
            maven(MavenPublication) {
                from components.java
            }
        }
    }
}
project(":project2") {
    apply plugin: 'maven'
    version = "2.0"
    archivesBaseName = "changed"
}
        """

        when:
        run "publish"

        then:

        project1.assertPublishedAsJavaModule()
        project1.parsedPom.scopes.runtime.assertDependsOn("org.gradle.test:project2:2.0")
    }

    private void createBuildScripts(String append = "") {
        settingsFile << """
include "project1", "project2", "project3"
        """

        buildFile << """
subprojects {
    apply plugin: "java"
    apply plugin: "maven-publish"

    publishing {
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        publications {
            maven(MavenPublication) {
                from components.java
            }
        }
    }
}

allprojects {
    group = "org.gradle.test"
    version = "3.0"
}

project(":project1") {
    version = "1.0"
    dependencies {
        compile project(":project2")
        compile project(":project3")
    }
}
project(":project2") {
    version = "2.0"
    dependencies {
        compile project(":project3")
    }
}

$append
        """
    }
}
