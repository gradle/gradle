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

package org.gradle.integtests.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.HttpServer
import org.gradle.internal.SystemProperties
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test
/**
 * @author: Szczepan Faber, created at: 6/16/11
 */
class MavenNewPublicationIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()

    void "publishes snapshot to a local maven repository"() {
        given:
        file('build.gradle') << """
apply plugin: 'java'
apply plugin: 'maven'
apply plugin: org.gradle.api.publication.PublicationPlugin

group = 'org.test'
archivesBaseName = 'someCoolProject'
version = '5.0-SNAPSHOT'

publications.maven.repository.url = '${mavenRepo.uri}'
"""

        when:
        executer.withTasks('publishArchives').run()

        then:
        def module = mavenRepo.module('org.test', 'someCoolProject', '5.0-SNAPSHOT')
        module.assertArtifactsPublished("someCoolProject-5.0-SNAPSHOT.jar", "someCoolProject-5.0-SNAPSHOT.pom")
    }

    @Test
    void "installs archives to local maven repo"() {
        given:
        file('build.gradle') << """
apply plugin: 'java'
apply plugin: 'maven'
apply plugin: org.gradle.api.publication.PublicationPlugin

group = 'org.test'
archivesBaseName = 'someCoolProject'
version = '5.0-SNAPSHOT'

"""

        when:
        executer.withTasks('installArchives').run()

        then:
        def localRepo = maven(new TestFile("$SystemProperties.userHome/.m2/repository"))
        def module = localRepo.module('org.test', 'someCoolProject', '5.0-SNAPSHOT')

        def files = module.moduleDir.list() as List
        assert files.contains('maven-metadata-local.xml')
        assert files.any { it =~ /someCoolProject-5.0-.*\.jar/ }
        assert files.any { it =~ /someCoolProject-5.0-.*\.pom/ }
    }

    void "publishes to remote maven repo"() {
        given:
        server.start()
        file('build.gradle') << """
apply plugin: 'java'
apply plugin: 'maven'
apply plugin: org.gradle.api.publication.PublicationPlugin

group = 'org.test'
archivesBaseName = 'someCoolProject'
version = '5.0'

publications {
    maven {
        repository {
            url = 'http://localhost:${server.port}/repo'
            credentials {
                username = 'szczepiq'
                password = 'secret'
            }
        }
    }
}

"""
        server.expectPut("/repo/org/test/someCoolProject/5.0/someCoolProject-5.0.jar", file("jar"))
        server.expectPut("/repo/org/test/someCoolProject/5.0/someCoolProject-5.0.jar.md5", file("jar.md5"))
        server.expectPut("/repo/org/test/someCoolProject/5.0/someCoolProject-5.0.jar.sha1", file("jar.sha1"))
        server.expectPut("/repo/org/test/someCoolProject/5.0/someCoolProject-5.0.pom", file("pom"))
        server.expectPut("/repo/org/test/someCoolProject/5.0/someCoolProject-5.0.pom.md5", file("pom.md5"))
        server.expectPut("/repo/org/test/someCoolProject/5.0/someCoolProject-5.0.pom.sha1", file("pom.sha1"))
        server.expectGetMissing("/repo/org/test/someCoolProject/maven-metadata.xml")
        server.expectPut("/repo/org/test/someCoolProject/maven-metadata.xml", file("metadata"))
        server.expectPut("/repo/org/test/someCoolProject/maven-metadata.xml.md5", file("metadata.md5"))
        server.expectPut("/repo/org/test/someCoolProject/maven-metadata.xml.sha1", file("metadata.sha1"))

        when:
        executer.withTasks('publishArchives').run()

        then:
        notThrown(Throwable)
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
