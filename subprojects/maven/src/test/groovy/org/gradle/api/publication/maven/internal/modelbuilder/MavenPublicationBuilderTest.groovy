/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.publication.maven.internal.modelbuilder

import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.publication.maven.MavenPublication
import org.gradle.api.publication.maven.MavenScope
import org.gradle.util.HelperUtil
import spock.lang.Ignore
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 5/13/11
 */
class MavenPublicationBuilderTest extends Specification {

    DefaultProject project = HelperUtil.createRootProject()

    //building the publication early, before the plugins and configurations are applied
    //to make sure that the conventionMappings are tied correctly and lazily evaluated
    MavenPublication publication = new MavenPublicationBuilder().build(project)

    def "populates model with basic information"() {
        when:
        project.apply(plugin: 'java')
        project.apply(plugin: 'maven')

        project.description = 'some test project'
        project.group = 'com.gradleware'

        project.jar {
            version = 1.8
            baseName = 'someJar'
        }

        then:
        publication.artifactId == 'someJar'
        publication.version == '1.8'

        publication.description == 'some test project'
        publication.groupId == 'com.gradleware'

        publication.packaging == 'jar'
        publication.modelVersion == '4.0.0'
    }

    def "honors archivesBaseName"() {
        when:
        project.apply(plugin: 'java')
        project.apply(plugin: 'maven')

        project.archivesBaseName = 'foobar'

        then:
        publication.artifactId == 'foobar'
        publication.mainArtifact.file.name == 'foobar.jar'
    }

    @Ignore
    //I don't think we want to support that...
    //the idea should be that the new publication dsl works when you configure the installation/deployment using the new DSL, not the old one
    def "populates model with info from installer configuration"() {
        when:
        project.apply(plugin: 'java')
        project.apply(plugin: 'maven')

        project.install {
            repositories.mavenInstaller.pom.project {
                groupId 'com.gradleware2'
            }
        }

        then:
        publication.groupId == 'com.gradleware2'
    }

    def "populates model with main artifact"() {
        when:
        project.apply(plugin: 'java')
        project.jar {
            classifier = 'jdk15'
            extension  = 'rambo'
        }

        then:
        publication.mainArtifact != null
        publication.mainArtifact.classifier == 'jdk15'
        publication.mainArtifact.extension == 'rambo'

        publication.mainArtifact.file != null
        publication.mainArtifact.file == project.jar.archivePath
    }

    def "populates model with compile dependencies"() {
        when:
        project.apply(plugin: 'java')
        project.repositories {
            mavenCentral()
        }
        project.dependencies {
           compile 'commons-lang:commons-lang:2.6'
           testCompile 'org.mockito:mockito-all:1.8.5'
        }

        then:
        publication.dependencies.size() == 2

        publication.dependencies[0].artifactId == 'commons-lang'
        publication.dependencies[0].groupId == 'commons-lang'
        publication.dependencies[0].classifier == null
        publication.dependencies[0].optional == false
        publication.dependencies[0].version == '2.6'
        publication.dependencies[0].scope == MavenScope.COMPILE

        publication.dependencies[1].artifactId == 'mockito-all'
        publication.dependencies[1].groupId == 'org.mockito'
        publication.dependencies[1].classifier == null
        publication.dependencies[1].optional == false
        publication.dependencies[1].version == '1.8.5'
        publication.dependencies[1].scope == MavenScope.TEST
    }

    def "populates model with runtime dependencies"() {
        when:
        project.apply(plugin: 'java')
        project.repositories {
            mavenCentral()
        }
        project.dependencies {
           runtime 'commons-lang:commons-lang:2.6'
           testRuntime 'org.mockito:mockito-all:1.8.5'
        }

        then:
        publication.dependencies.size() == 2

        publication.dependencies[0].artifactId == 'commons-lang'
        publication.dependencies[0].groupId == 'commons-lang'
        publication.dependencies[0].classifier == null
        publication.dependencies[0].optional == false
        publication.dependencies[0].version == '2.6'
        publication.dependencies[0].scope == MavenScope.RUNTIME

        publication.dependencies[1].artifactId == 'mockito-all'
        publication.dependencies[1].groupId == 'org.mockito'
        publication.dependencies[1].classifier == null
        publication.dependencies[1].optional == false
        publication.dependencies[1].version == '1.8.5'
        publication.dependencies[1].scope == MavenScope.TEST
    }

    def "populates model with dependency with a classifier"() {
        when:
        project.apply(plugin: 'java')
        project.dependencies {
           testCompile 'org.foo:bar:1.0:testUtil'
        }

        then:
        publication.dependencies.size() == 1

        publication.dependencies[0].artifactId == 'bar'
        publication.dependencies[0].groupId == 'org.foo'
        publication.dependencies[0].classifier == 'testUtil'
        publication.dependencies[0].optional == false
        publication.dependencies[0].version == '1.0'
        publication.dependencies[0].scope == MavenScope.TEST
    }

    def "does not break when java plugin not applied and has reasonable defaults"() {
        expect:
        !publication.artifactId
        !publication.dependencies
        !publication.description
        publication.groupId
        publication.mainArtifact
        publication.modelVersion
        !publication.packaging
        !publication.pom
        publication.properties
        !publication.subArtifacts
        publication.version
    }

    def "does not break when file dependencies are configured"() {
        when:
        project.apply(plugin: 'java')
        project.dependencies {
           compile project.files('sample.jar')
        }

        then:
        publication.dependencies.size() == 0
    }
}
