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
import org.gradle.tooling.model.GradleProject
import spock.lang.Ignore

class PublicationsCrossVersionSpec extends ToolingApiSpecification {
    @ToolingApiVersion('current')
    @TargetGradleVersion('current')
    def "GradleProject provides publication for each uploadArchives task with Ivy repository"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
"""
apply plugin: "base"

version = 1.0
group = "test.group"

uploadArchives {
    repositories {
        ivy { url "file:///\$buildDir/ivy-repo" }
    }
}
"""

        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.publications.size() == 1
        with(project.publications.iterator().next()) {
            id.group == "test.group"
            id.name == "test.project"
            id.version == "1.0"
        }
    }

    @Ignore
    @ToolingApiVersion('current')
    @TargetGradleVersion('current')
    def "GradleProject provides publication for each uploadArchives task with Maven repository"() {
        settingsFile << "rootProject.name = 'test.project'"
        buildFile <<
"""
apply plugin: "maven"

version = 1.0
group = "test.group"

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "file:///\$buildDir/maven-repo")
        }
    }
}
"""

        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.publications.size() == 1
        with(project.publications.iterator().next()) {
            id.group == "test.group"
            id.name == "test.project"
            id.version == "1.0"
        }
    }

    @Ignore
    @ToolingApiVersion('current')
    @TargetGradleVersion('current')
    def "GradleProject provides publication for each publication added to publishing.publications"() {
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
        ivy { url "file:///\$buildDir/ivy-repo" }
        maven { url "file:///\$buildDir/maven-repo" }
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
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.publications.size() == 2

        and:
        def pub1 = project.publications.find { it.id.group == "test.org" }
        pub1 != null
        pub1.id.name == "test-module"
        pub1.id.version == "1.1"

        and:
        def pub2 = project.publications.find { it.id.group == "test.groupId" }
        pub2 != null
        pub2.id.name == "test-artifactId"
        pub2.id.version == "1.2"
    }
}
