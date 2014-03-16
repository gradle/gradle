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
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildActionFailureException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.gradle.ProjectPublications

@ToolingApiVersion('>=1.12')
@TargetGradleVersion('>=1.12')
class PublicationsCrossVersionSpec extends ToolingApiSpecification {
    def "project without any configured publications"() {
        buildFile << "apply plugin: 'java'"

        when:
        ProjectPublications publications = withConnection { connection ->
            connection.action(new FetchPublicationsBuildAction()).run()
        }

        then:
        publications.publications.empty
    }

    def "Ivy repository based publication"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
"""
apply plugin: "base"

version = 1.0
group = "test.group"

uploadArchives {
    repositories {
        ivy { url uri("\$buildDir/ivy-repo") }
    }
}
"""

        when:
        ProjectPublications publications = withConnection { connection ->
            connection.action(new FetchPublicationsBuildAction()).run()
        }

        then:
        publications.publications.size() == 1
        with(publications.publications.iterator().next()) {
            id.group == "test.group"
            id.name == "test.project"
            id.version == "1.0"
        }
    }

    def "Maven repository based publication with coordinates inferred from project"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
"""
apply plugin: "maven"

version = 1.0
group = "test.group"

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri("\$buildDir/maven-repo"))
        }
    }
}
"""

        when:
        ProjectPublications publications = withConnection { connection ->
            connection.action(new FetchPublicationsBuildAction()).run()
        }

        then:
        publications.publications.size() == 1
        with(publications.publications.iterator().next()) {
            id.group == "test.group"
            id.name == "test.project"
            id.version == "1.0"
        }
    }

    def "Maven repository based publication with coordinates inferred from POM configuration"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
                """
apply plugin: "maven"

version = 1.0
group = "test.group"

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri("\$buildDir/maven-repo"))
            pom.groupId = "test.groupId"
            pom.artifactId = "test.artifactId"
            pom.version = "1.1"
        }
    }
}
"""

        when:
        ProjectPublications publications = withConnection { connection ->
            connection.action(new FetchPublicationsBuildAction()).run()
        }

        then:
        publications.publications.size() == 1
        with(publications.publications.iterator().next()) {
            id.group == "test.groupId"
            id.name == "test.artifactId"
            id.version == "1.1"
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
        ivy { url uri("\$buildDir/ivy-repo") }
        maven { url uri("\$buildDir/maven-repo") }
    }
    publications {
        mainIvy(IvyPublication) {
            from components.java
            organisation 'test.org'
            module 'test-module'
            revision '1.1'
        }
        mainMaven(MavenPublication) {
            from components.java
            groupId 'test.groupId'
            artifactId 'test-artifactId'
            version '1.2'
        }
    }
}
"""

        when:
        ProjectPublications publications = withConnection { connection ->
            connection.action(new FetchPublicationsBuildAction()).run()
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

    @TargetGradleVersion('<1.12 >=1.8')
    def "decent error message for Gradle version that doesn't expose publications"() {
        when:
        ProjectPublications publications = withConnection { connection ->
            connection.action(new FetchPublicationsBuildAction()).run()
        }
        publications.publications

        then:
        BuildActionFailureException e = thrown()
        e.cause.message.contains('No model of type \'ProjectPublications\' is available in this build.')
    }

    @TargetGradleVersion('<1.8')
    def "decent error message for Gradle version that doesn't expose build actions"() {
        when:
        ProjectPublications publications = withConnection { connection ->
            connection.action(new FetchPublicationsBuildAction()).run()
        }
        publications.publications

        then:
        UnsupportedVersionException e = thrown()
        e.message.contains('The version of Gradle you are using')
        e.message.contains('does not support execution of build actions provided by the tooling API client.')
    }
}
