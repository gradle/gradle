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

package org.gradle.api.publication.maven.internal

import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.publication.maven.MavenPublication
import org.gradle.api.publication.maven.MavenScope
import org.gradle.util.HelperUtil
import spock.lang.Ignore
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 5/13/11
 */
class MavenModelBuilderTest extends Specification {

    def builder = new MavenModelBuilder()
    DefaultProject project = HelperUtil.createRootProject()

    def "populates model with basic information"() {
        project.apply(plugin: 'java')
        project.apply(plugin: 'maven')

        project.description = 'some test project'
        project.group = 'com.gradleware'

        project.jar {
            version = 1.8
            baseName = 'someJar'
        }

        when:
        MavenPublication publication = builder.build(project)

        then:
        publication.artifactId == 'someJar'
        publication.version == '1.8'

        publication.description == 'some test project'
        publication.groupId == 'com.gradleware'

        publication.packaging == 'jar'
        publication.modelVersion == '4.0.0'
    }

    @Ignore
    //I don't think we want to support that...
    //the idea should be that the new publication dsl works when you configure the installation/deployment using the new DSL, not the old one
    def "populates model with info from installer configuration"() {
        project.apply(plugin: 'java')
        project.apply(plugin: 'maven')

        project.install {
            repositories.mavenInstaller.pom.project {
                groupId 'com.gradleware2'
            }
        }

        when:
        MavenPublication publication = builder.build(project)

        then:
        publication.groupId == 'com.gradleware2'
    }

    def "populates model with main artifact"() {
        project.apply(plugin: 'java')
        project.jar {
            classifier = 'jdk15'
            extension  = 'rambo'
        }

        when:
        MavenPublication publication = builder.build(project)

        then:
        publication.mainArtifact != null
        publication.mainArtifact.classifier == 'jdk15'
        publication.mainArtifact.extension == 'rambo'

        publication.mainArtifact.file != null
        publication.mainArtifact.file == project.jar.archivePath
    }

    def "populates model with compile dependencies"() {
        project.apply(plugin: 'java')
        project.repositories {
            mavenCentral()
        }
        project.dependencies {
           compile 'commons-lang:commons-lang:2.6'
           testCompile 'org.mockito:mockito-all:1.8.5'
        }

        when:
        MavenPublication publication = builder.build(project)

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
        project.apply(plugin: 'java')
        project.repositories {
            mavenCentral()
        }
        project.dependencies {
           runtime 'commons-lang:commons-lang:2.6'
           testRuntime 'org.mockito:mockito-all:1.8.5'
        }

        when:
        MavenPublication publication = builder.build(project)

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
}
