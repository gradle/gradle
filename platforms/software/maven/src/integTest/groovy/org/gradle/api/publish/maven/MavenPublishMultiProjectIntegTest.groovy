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

    def "project dependencies reference publication identity of dependent project (version mapping: #mapping)"() {
        def project3 = javaLibrary(mavenRepo.module("changed.group", "changed-artifact-id", "changed"))

        def extra = """
project(":project3") {
    publishing {
        publications.maven {
            groupId "changed.group"
            artifactId "changed-artifact-id"
            version "changed"
        }
    }
}
"""
        if (mapping) {
            extra = """
project(":project1") {
    publishing {
        publications.maven {
            versionMapping {
                usage(Usage.JAVA_API) {
                    fromResolutionResult()
                }
            }
        }
    }
}
""" + extra
        }
        createBuildScripts(extra)

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

        where:
        mapping << [false, true]
    }

    def "reports failure when project dependency references a project with multiple conflicting publications"() {
        createBuildScripts("""
project(":project2") {
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
Found the following publications in project ':project2':
  - Maven publication 'maven' with coordinates org.gradle.test:project2:2.0
  - Maven publication 'extraComp' with coordinates extra.group:extra-comp:extra
  - Maven publication 'extra' with coordinates extra.group:extra:extra"""
    }

    def "referenced project can have additional non-component publications"() {
        createBuildScripts("""
project(":project3") {
    publishing {
        publications {
            extra(MavenPublication) {
                groupId "extra.group"
                artifactId "extra"
                version "extra"
            }
        }
    }
}
""")

        expect:
        succeeds "publish"
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
    def c1 = new ExtraComp(variants: [components.java])
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
    base {
        archivesName = "changed"
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

    @Issue("GRADLE-3366")
    def "project dependency excludes are correctly reflected in pom when using maven-publish plugin"() {
        given:
        createDirs("project1", "project2")
        settingsFile << """
include "project1", "project2"
"""

        buildFile << """
allprojects {
    apply plugin: 'java-library'
    apply plugin: 'maven-publish'

    group = "org.gradle.test"

    ${mavenCentralRepository()}
}

project(":project1") {
    version = "1.0"

    dependencies {
        api "commons-collections:commons-collections:3.2.2"
        api "commons-io:commons-io:1.4"
    }
}

project(":project2") {
    version = "2.0"

    dependencies {
        api project(":project1"), {
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

        project2.parsedModuleMetadata.variant('apiElements') {
            dependency('org.gradle.test:project1:1.0') {
                exists()
                hasExclude('*', 'commons-collections')
                hasExclude('commons-io', '*')
                noMoreExcludes()
            }
        }
    }

    def "publish and resolve java-library with dependency on java-platform (named #platformName)"() {
        given:
        javaLibrary(mavenRepo.module("org.test", "foo", "1.0")).withModuleMetadata().publish()
        javaLibrary(mavenRepo.module("org.test", "bar", "1.1")).withModuleMetadata().publish()

        createDirs(platformName, "library")
        settingsFile << """
include "$platformName", "library"
"""

        buildFile << """
allprojects {
    apply plugin: 'maven-publish'

    group = "org.test"
    version = "1.0"

    ${mavenTestRepository()}
}

project(":$platformName") {
    apply plugin: 'java-platform'

    javaPlatform {
        allowDependencies()
    }

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
            maven(MavenPublication) { from components.javaPlatform }
        }
    }
}

project(":library") {
    apply plugin: 'java-library'

    dependencies {
        api platform(project(":$platformName"))
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

        def platformModule = mavenRepo.module("org.test", platformName, "1.0").removeGradleMetadataRedirection()
        def libraryModule = mavenRepo.module("org.test", "library", "1.0").removeGradleMetadataRedirection()

        then:
        platformModule.parsedPom.packaging == 'pom'
        platformModule.parsedPom.scopes.compile.assertDependsOn("org.test:foo:1.0")
        platformModule.parsedPom.scopes.no_scope.assertDependencyManagement("org.test:bar:1.1")
        platformModule.parsedModuleMetadata.variant('apiElements') {
            dependency("org.test:foo:1.0").exists()
            constraint("org.test:bar:1.1").exists()
            noMoreDependencies()
        }

        libraryModule.parsedPom.packaging == null
        libraryModule.parsedPom.scopes.compile.assertDependsOn("org.test:bar:")
        libraryModule.parsedPom.scopes.compile.assertDependencyManagement()
        libraryModule.parsedPom.scopes['import'].expectDependencyManagement("org.test:$platformName:1.0").hasType('pom')
        libraryModule.parsedModuleMetadata.variant('apiElements') {
            dependency("org.test:bar:").exists()
            dependency("org.test:$platformName:1.0").exists()
            noMoreDependencies()
        }

        and:
        resolveArtifacts(platformModule) { expectFiles 'foo-1.0.jar' }
        resolveArtifacts(libraryModule) {
            withModuleMetadata {
                expectFiles 'bar-1.1.jar', 'foo-1.0.jar', 'library-1.0.jar'
            }
            withoutModuleMetadata {
                // This is caused by the dependency on the platform appearing as a dependencyManagement entry with scope=import, type=pom
                // and thus its dependencies are ignored.
                expectFiles 'bar-1.1.jar', 'library-1.0.jar'
            }
        }

        where:
        platformName << ['platform', 'aplatform']
    }

    private void createBuildScripts(String append = "") {
        createDirs("project1", "project2", "project3")
        settingsFile << """
include "project1", "project2", "project3"
        """

        buildFile << """
subprojects {
    apply plugin: "java-library"
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
        api project(":project2")
        api project(":project3")
    }
}
project(":project2") {
    version = "2.0"
    dependencies {
        api project(":project3")
    }
}

$append
        """
    }
}
