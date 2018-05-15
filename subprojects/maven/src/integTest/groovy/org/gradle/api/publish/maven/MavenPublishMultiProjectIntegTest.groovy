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
import spock.lang.Issue

class MavenPublishMultiProjectIntegTest extends AbstractMavenPublishIntegTest {
    def project1 = javaLibrary(mavenRepo.module("org.gradle.test", "project1", "1.0"))
    def project2 = javaLibrary(mavenRepo.module("org.gradle.test", "project2", "2.0"))
    def project3 = javaLibrary(mavenRepo.module("org.gradle.test", "project3", "3.0"))

    def "project dependency correctly reflected in POM"() {
        createBuildScripts()

        when:
        run "publish"

        then:
        projectsCorrectlyPublished()
    }

    def "project dependencies reference publication identity of dependent project"() {
        def project3 = javaLibrary(mavenRepo.module("changed.group", "changed-artifact-id", "changed"))

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
        project1.assertPublished()
        project1.assertApiDependencies("changed.group:changed-artifact-id:changed", "org.gradle.test:project2:2.0")

        project2.assertPublished()
        project2.assertApiDependencies("changed.group:changed-artifact-id:changed")

        project3.assertPublished()
        project3.assertNoDependencies()

        and:
        resolveArtifacts(project1) {
            expectFiles 'changed-artifact-id-changed.jar', 'project1-1.0.jar', 'project2-2.0.jar'
        }
    }

    def "reports failure when project dependency references a project with multiple publications"() {
        createBuildScripts("""
project(":project3") {
    publishing {
        publications {
            extraComp(MavenPublication) {
                from components.java
                groupId "extra.group"
                artifactId "extra-comp"
                version "extra"
            }
            extra(MavenPublication) {
                groupId "extra.group"
                artifactId "extra"
                version "extra"
            }
        }
    }
}
""")

        when:
        fails "publish"

        then:
        failure.assertHasCause """Publishing is not able to resolve a dependency on a project with multiple publications that have different coordinates.
Found the following publications in project ':project3':
  - Maven publication 'maven' with coordinates org.gradle.test:project3:3.0
  - Maven publication 'extraComp' with coordinates extra.group:extra-comp:extra
  - Maven publication 'extra' with coordinates extra.group:extra:extra"""
    }

    def "referenced project can have multiple additional publications that contain a child of some other publication"() {
        createBuildScripts("""
// TODO - replace this with a public API when available
class ExtraComp implements org.gradle.api.internal.component.SoftwareComponentInternal, ComponentWithVariants {
    String name = 'extra'
    Set usages = []
    Set variants = []
}

project(":project3") {
    def c1 = new ExtraComp()
    def c2 = new ExtraComp(variants: [c1, components.java])
    publishing {
        publications {
            extra1(MavenPublication) {
                from c1
                groupId "extra.group"
                artifactId "extra1"
                version "extra"
            }
            extra2(MavenPublication) {
                from c2
                groupId "custom"
                artifactId "custom3"
                version "456"
            }
        }
    }
}
""")

        when:
        succeeds "publish"

        then:
        project1.assertApiDependencies("org.gradle.test:project2:2.0", "custom:custom3:456")
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
        project1.assertPublished()
        project1.assertApiDependencies("org.gradle.test:project2:2.0", "org.gradle.test:project3:3.0")

        project2.assertPublished()
        project2.assertApiDependencies("org.gradle.test:project3:3.0")

        project3.assertPublished()
        project3.assertNoDependencies()

        resolveArtifacts(project1) { expectFiles "project1-1.0.jar", "project2-2.0.jar", "project3-3.0.jar" }

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
        project1.assertPublished()
        project1.assertApiDependencies("org.gradle.test:project2:2.0")
    }

    @Issue("GRADLE-3366")
    def "project dependency excludes are correctly reflected in pom when using maven-publish plugin"() {
        given:
        settingsFile << """
include "project1", "project2"
"""

        buildFile << """
allprojects {
    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = "org.gradle.test"

    ${mavenCentralRepository()}
}

project(":project1") {
    version = "1.0"

    dependencies {
        compile "commons-collections:commons-collections:3.2.2"
        compile "commons-io:commons-io:1.4"
    }
}

project(":project2") {
    version = "2.0"

    dependencies {
        compile project(":project1"), {
            exclude module: "commons-collections"
            exclude group: "commons-io"
        }
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
"""
        when:
        run "publish"

        then:
        project2.assertPublished()
        project2.assertApiDependencies("org.gradle.test:project1:1.0")

        def dep = project2.parsedPom.scopes.compile.expectDependency("org.gradle.test:project1:1.0")
        dep.exclusions.size() == 2
        def sorted = dep.exclusions.sort { it.groupId }
        sorted[0].groupId == "*"
        sorted[0].artifactId == "commons-collections"
        sorted[1].groupId == "commons-io"
        sorted[1].artifactId == "*"

        project2.parsedModuleMetadata.variant('api') {
            dependency('org.gradle.test:project1:1.0') {
                exists()
                hasExclude('*', 'commons-collections')
                hasExclude('commons-io', '*')
                noMoreExcludes()
            }
        }
    }

    def "publish and resolve java-library with dependency on java-library-platform"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        settingsFile << """
include "platform", "library"
"""

        buildFile << """
allprojects {
    apply plugin: 'maven-publish'
    apply plugin: 'java-library'

    group = "org.test"
    version = "1.0"
}

project(":platform") {
    dependencies {
        api "org.test:foo:1.0"
        constraints {
            api "org.test:bar:1.1"
        }
    }
    publishing {
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        publications {
            maven(MavenPublication) { from components.javaLibraryPlatform }
        }
    }
}

project(":library") {
    dependencies {
        api project(":platform")
        api "org.test:bar"
    }
    publishing {
        repositories {
            maven { url "${mavenRepo.uri}" }
        }
        publications {
            maven(MavenPublication) { from components.java }
        }
    }
}
"""
        when:
        run "publish"

        def platformModule = mavenRepo.module("org.test", "platform", "1.0")
        def libraryModule = mavenRepo.module("org.test", "library", "1.0")

        then:
        platformModule.parsedPom.packaging == 'pom'
        platformModule.parsedPom.scopes.compile.assertDependsOn("org.test:foo:1.0")
        platformModule.parsedPom.scopes.compile.assertDependencyManagement("org.test:bar:1.1")
        platformModule.parsedModuleMetadata.variant('api') {
            dependency("org.test:foo:1.0").exists()
            constraint("org.test:bar:1.1").exists()
            noMoreDependencies()
        }

        libraryModule.parsedPom.packaging == null
        libraryModule.parsedPom.scopes.compile.assertDependsOn("org.test:bar:", "org.test:platform:1.0")
        libraryModule.parsedPom.scopes.compile.assertDependencyManagement()
        libraryModule.parsedModuleMetadata.variant('api') {
            dependency("org.test:bar:").exists()
            dependency("org.test:platform:1.0").exists()
            noMoreDependencies()
        }

        and:
        resolveArtifacts(platformModule) { expectFiles 'foo-1.0.jar' }
        resolveArtifacts(libraryModule) {
            expectFiles 'bar-1.1.jar', 'foo-1.0.jar', 'library-1.0.jar'
        }
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
