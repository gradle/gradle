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

class IvyPublishMultiProjectIntegTest extends AbstractIntegrationSpec {
    def project1 = ivyRepo.module("org.gradle.test", "project1", "1.9")
    def project2 = ivyRepo.module("org.gradle.test", "project2", "1.9")
    def project3 = ivyRepo.module("org.gradle.test", "project3", "1.9")

    def "project dependencies correctly reflected in descriptor"() {
        createBuildScripts("")

        when:
        run "publish"

        then:
        project1.assertPublishedAsJavaModule()
        project1.ivy.dependencies.runtime.assertDependsOnModules("project2", "project3")

        project2.assertPublishedAsJavaModule()
        project2.ivy.dependencies.runtime.assertDependsOnModules("project3")

        project3.assertPublishedAsJavaModule()
        project3.ivy.dependencies.runtime == null
    }

    def "can resolve published project"() {
        when:
        createBuildScripts("")
        succeeds "publish"

        then:
        project1.assertPublishedAsJavaModule()

        when:
        settingsFile << ""
        buildFile << """
apply plugin: 'java'

repositories {
    ivy { url "${ivyRepo.uri}" }
}
dependencies {
    compile "org.gradle.test:project1:1.9"
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        then:
        succeeds "retrieve"

        and:
        file('libs').assertHasDescendants('project1-1.9.jar', 'project2-1.9.jar', 'project3-1.9.jar')
    }

    def "ivy-publish plugin does not take archivesBaseName into account for publication name"() {
        createBuildScripts("""
project(":project2") {
    archivesBaseName = "changed"
}
        """)

        when:
        run "publish"

        then:
        project1.assertPublishedAsJavaModule()
        project1.ivy.dependencies.runtime.assertDependsOnModules("project2", "project3")

        // published with the correct coordinates, even though artifact has different name
        project2.assertPublished()
        project2.assertArtifactsPublished("changed-${project2.revision}.jar", "ivy-${project2.revision}.xml")
        project2.ivy.dependencies.runtime.assertDependsOnModules("project3")

        project3.assertPublishedAsJavaModule()
        project3.ivy.dependencies.runtime == null
    }

    def "ivy-publish plugin uses target project name for project dependency when target project does not have ivy-publish plugin applied"() {
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
    apply plugin: "ivy-publish"

    dependencies {
        compile project(":project2")
    }

    publishing {
        repositories {
            ivy { url "${ivyRepo.uri}" }
        }
        publications {
            ivy(IvyPublication) {
                from components.java
            }
        }
    }
}
project(":project2") {
    apply plugin: 'java'
    archivesBaseName = "changed"
}
        """

        when:
        run "publish"

        then:
        project1.assertPublishedAsJavaModule()
        project1.ivy.dependencies.runtime.assertDependsOn("org.gradle.test", "project2", "1.9")
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
    apply plugin: "ivy-publish"
}

// Need to configure subprojects before publications, due to non-laziness. This is something we need to address soon.
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

$append

subprojects {
    publishing {
        repositories {
            ivy { url "${ivyRepo.uri}" }
        }
        publications {
            ivy(IvyPublication) {
                from components.java
            }
        }
    }
}
        """
    }
}
