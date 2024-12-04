/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r112

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.gradle.ProjectPublications

class PublicationsCrossVersionSpec extends ToolingApiSpecification {
    def "empty project"() {
        when:
        ProjectPublications publications = withConnection { connection ->
            connection.getModel(ProjectPublications)
        }

        then:
        publications.publications.empty
    }

    def "project without any configured publications"() {
        buildFile << "apply plugin: 'java'"

        when:
        ProjectPublications publications = withConnection { connection ->
            connection.getModel(ProjectPublications)
        }

        then:
        publications.publications.empty
    }

    @TargetGradleVersion(">=3.0 <7.0")
    def "Ivy repository based publication"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
"""
apply plugin: "base"

version = 1.0
group = "test.group"

uploadArchives {
    repositories {
        ivy { url = uri("ivy-repo") }
    }
}
"""

        when:
        ProjectPublications publications = withConnection { connection ->
            connection.getModel(ProjectPublications)
        }

        then:
        publications.publications.size() == 1
        with(publications.publications.iterator().next()) {
            id.group == "test.group"
            id.name == "test.project"
            id.version == "1.0"
        }
    }

    def "publishing.publications based publication"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
                """
apply plugin: "ivy-publish"
apply plugin: "maven-publish"
apply plugin: "java"

version = 1.0
group = "test.group"

publishing {
    repositories {
        ivy { url = uri("ivy-repo") }
        maven { url = uri("maven-repo") }
    }
    publications {
        mainIvy(IvyPublication) {
            from components.java
            organisation = 'test.org'
            module = 'test-module'
            revision = '1.1'
        }
        mainMaven(MavenPublication) {
            from components.java
            groupId = 'test.groupId'
            artifactId = 'test-artifactId'
            version = '1.2'
        }
    }
}
"""

        when:
        ProjectPublications publications = withConnection { connection ->
            connection.getModel(ProjectPublications)
        }

        then:
        publications.publications.size() == 2

        and:
        def pub1 = publications.publications.find { it.id.group == "test.org" }
        pub1 != null
        pub1.id.name == "test-module"
        pub1.id.version == "1.1"

        and:
        def pub2 = publications.publications.find { it.id.group == "test.groupId" }
        pub2 != null
        pub2.id.name == "test-artifactId"
        pub2.id.version == "1.2"
    }
}
