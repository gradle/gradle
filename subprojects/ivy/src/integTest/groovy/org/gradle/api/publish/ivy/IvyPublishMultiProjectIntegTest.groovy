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
import spock.lang.Ignore

class IvyPublishMultiProjectIntegTest extends AbstractIntegrationSpec {
    def project1module = ivyRepo.module("org.gradle.test", "project1", "1.9")

    def "project dependency correctly reflected in descriptor"() {
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

    @Ignore // TODO:DAZ Fix this
    def "ivy-publish plugin does not take archivesBaseName into account when publishing"() {
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
            ivy { url "file:///\$rootProject.projectDir/ivy-repo" }
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
        project1module.assertPublishedAsJavaModule()
        project1module.ivy.dependencies.runtime.assertDependsOn("org.gradle.test", "project2", "1.9")
    }


    private def projectsCorrectlyPublished() {
        def project2 = ivyRepo.module("org.gradle.test", "project2", "1.9")
        project2.assertPublishedAsJavaModule()

        project1module.ivy.dependencies.runtime.assertDependsOn("org.gradle.test", "project2", "1.9")

        return true
    }

    def "multiple project dependencies correctly reflected in ivy descriptor"() {
        def project2module = ivyRepo.module("org.gradle.test", "project2", "1.9")
        def project3module = ivyRepo.module("org.gradle.test", "project3", "1.9")

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
        project1module.ivy.dependencies["runtime"].assertDependsOnModules("project2", "project3")
        project2module.ivy.dependencies["runtime"].assertDependsOnModules("project3")
        project3module.ivy.dependencies["runtime"] == null
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
$append

subprojects {
    publishing {
        repositories {
            ivy { url "file:///\$rootProject.projectDir/ivy-repo" }
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
