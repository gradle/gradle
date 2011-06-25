/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.publication.maven

import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.gradle.util.SystemProperties
import org.gradle.util.TestFile
import org.junit.Ignore
import org.junit.Test

import static org.gradle.util.TextUtil.escapeString

/**
 * @author: Szczepan Faber, created at: 6/16/11
 */
class MavenPublicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void "publishes snapshot to a flat dir maven repo"() {
        //given
        def repo = file("repo").createDir()

        file('build.gradle') << """
apply plugin: 'java'
apply plugin: 'maven'
new org.gradle.api.publication.PublicationPlugin().apply(project)

group = 'org.test'
archivesBaseName = 'someCoolProject'
version = '5.0-SNAPSHOT'

repositories {
    mavenCentral()
}

publications.maven.repository.url = '${escapeString(repo.toURL())}'
"""
        //when
        executer.withTasks('publishArchives').run()

        //then
        def result = repo.file('org', 'test', 'someCoolProject', '5.0-SNAPSHOT')
        def files = result.list() as List
        assert files.contains('maven-metadata.xml')
        assert files.any { it =~ /someCoolProject-5.0-.*\.jar/ }
    }

    @Test
    void "installs archives to local maven repo"() {
        //given
        file('build.gradle') << """
apply plugin: 'java'
apply plugin: 'maven'
new org.gradle.api.publication.PublicationPlugin().apply(project)

group = 'org.test'
archivesBaseName = 'someCoolProject'
version = '5.0-SNAPSHOT'

repositories {
    mavenCentral()
}
"""
        //when
        executer.withTasks('installArchives').run()

        //then
        def localRepo = new TestFile("$SystemProperties.userHome/.m2/repository")
        def result = localRepo.file('org', 'test', 'someCoolProject', '5.0-SNAPSHOT')
        def files = result.list() as List
        assert files.contains('maven-metadata-local.xml')
        assert files.any { it =~ /someCoolProject-5.0-.*\.jar/ }
    }

    //at this moment this is a half manual test.
    //we may delete it or configure artifactory to enable integration testing them
    @Ignore
    @Test
    void "publishes to remote maven repo"() {
        //given
        file('build.gradle') << """
apply plugin: 'java'
apply plugin: 'maven'
new org.gradle.api.publication.PublicationPlugin().apply(project)

repositories {
    mavenCentral()
}

publications {
    maven {
        repository {
            url = 'http://repo.gradle.org/gradle/integ-tests'
            authentication {
                userName = 'szczepiq'
                password = 'secret'
            }
        }
    }
}

"""
        //when
        executer.withTasks('publishMaven').run()

        //then
    }

        //maven {
//      groupId
//      artifactId
//      classifier "jdk15"
//      extension "jar"
//      artifacts {
//        main "build/foo.jar"
//        sources "build/sources.jar"
//        javadoc "build/javadoc.jar"
//        others?
//      }
//      dependencies {
//        compile ...
//        runtime ...
//        test ...
//        provided ...
//        system ....
//      }
//      pom {
//        whenConfigured {}
//        contributors {
//          contributor {
//            name "fred firestone"
//          }
//        }
//      }
//      pom.whenConfigured { Model model -> }
//      pom.withXml { }
//    }
}
