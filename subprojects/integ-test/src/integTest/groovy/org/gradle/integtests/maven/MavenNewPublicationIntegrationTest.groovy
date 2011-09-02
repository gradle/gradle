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

package org.gradle.integtests.maven

import org.gradle.integtests.fixtures.GradleDistributionExecuter.Executer
import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.gradle.util.SystemProperties
import org.gradle.util.TestFile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import spock.lang.Ignore

/**
 * @author: Szczepan Faber, created at: 6/16/11
 */
class MavenNewPublicationIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()

    @Before
    public void setup() {
        // TODO - need to fix this. Currently, you must run the 'intTestImage' task before running this test.
        executer.type = Executer.forking
    }

    @Test
    void "publishes snapshot to a local maven repository"() {
        given:
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

publications.maven.repository.url = '${repo().rootDir.toURI()}'
"""

        when:
        executer.withTasks('publishArchives').run()

        then:
        def module = repo().module('org.test', 'someCoolProject', '5.0-SNAPSHOT')
        module.assertArtifactsDeployed("someCoolProject-5.0-SNAPSHOT.jar")
    }

    @Test
    void "installs archives to local maven repo"() {
        given:
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

        when:
        executer.withTasks('installArchives').run()

        then:
        def localRepo = new MavenRepository(new TestFile("$SystemProperties.userHome/.m2/repository"))
        def module = localRepo.module('org.test', 'someCoolProject', '5.0-SNAPSHOT')

        def files = module.moduleDir.list() as List
        assert files.contains('maven-metadata-local.xml')
        assert files.any { it =~ /someCoolProject-5.0-.*\.jar/ }
    }

    @Ignore
    @Test
    void "publishes to remote maven repo"() {
        given:
        server.start()
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
            url = 'http://localhost:${server.port}/repo'
            authentication {
                userName = 'szczepiq'
                password = 'secret'
            }
        }
    }
}

"""

        when:
        executer.withTasks('publishArchives').run()

        then:
        def module = repo().module('org.test', 'someCoolProject', '5.0-SNAPSHOT')
        module.assertArtifactsDeployed("someCoolProject-5.0-SNAPSHOT.jar")
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

    def MavenRepository repo() {
        new MavenRepository(distribution.testFile('mavenRepo'))
    }
}
