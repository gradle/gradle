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

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.MavenRepository
import org.junit.Test
import org.gradle.integtests.fixtures.HttpServer

class MavenPublicationIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources testResources = new TestResources()
    @Rule public final HttpServer server = new HttpServer()

    @Test
    public void canPublishAProjectWithDependencyInMappedAndUnMappedConfiguration() {
        executer.withTasks('uploadArchives').run()
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsPublished('root-1.0.jar', 'root-1.0.pom')
    }

    @Test
    public void canPublishAProjectWithNoMainArtifact() {
        executer.withTasks('uploadArchives').run()
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsPublished('root-1.0-source.jar')
    }

    @Test
    public void canPublishAProjectWithMetadataArtifacts() {
        executer.withTasks('uploadArchives').run()
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsPublished('root-1.0.jar', 'root-1.0.jar.sig', 'root-1.0.pom', 'root-1.0.pom.sig')
    }

    @Test
    public void canPublishASnapshotVersion() {
        dist.testFile('build.gradle') << """
apply plugin: 'java'
apply plugin: 'maven'

group = 'org.gradle'
version = '1.0-SNAPSHOT'
archivesBaseName = 'test'

uploadArchives {
    repositories {
        mavenDeployer {
            snapshotRepository(url: uri("mavenRepo"))
        }
    }
}
"""

        executer.withTasks('uploadArchives').run()

        def module = repo().module('org.gradle', 'test', '1.0-SNAPSHOT')
        module.assertArtifactsPublished('test-1.0-SNAPSHOT.jar', 'test-1.0-SNAPSHOT.pom')
    }

    @Test
    public void canPublishesMultipleDeploymentsWithAttachedArtifacts() {
        server.start()

        dist.testFile('settings.gradle') << "rootProject.name = 'someCoolProject'"
        dist.testFile('build.gradle') << """
apply plugin:'java'
apply plugin:'maven'

version = 1.0
group =  "org.test"

task sourcesJar(type: Jar) {
	    from sourceSets.main.allSource
	    classifier = 'sources'
}

task testJar(type: Jar) {
	    baseName = project.name + '-tests'
}

task testSourcesJar(type: Jar) {
	    baseName = project.name + '-tests'
	    classifier = 'sources'
}

artifacts { archives sourcesJar, testJar, testSourcesJar }

uploadArchives {
    repositories{
        mavenDeployer {
            repository(url: "http://localhost:${server.port}/repo") {
   		        authentication(userName: "testuser", password: "secret")
	        }
            addFilter('main') {artifact, file ->
		        artifact.name.endsWith("-tests")
		    }
			addFilter('tests') {artifact, file ->
			    artifact.name.endsWith("-tests")
			}
        }
    }
}
"""
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.pom", dist.testFile("pom"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.pom.md5", dist.testFile("pom.md5"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.pom.sha1", dist.testFile("pom.sha1"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.jar", dist.testFile("jar"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.jar.md5", dist.testFile("jar.md5"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.jar.sha1", dist.testFile("jar.sha1"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0-sources.jar", dist.testFile("sources.jar"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0-sources.jar.md5", dist.testFile("sources.md5"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0-sources.jar.sha1", dist.testFile("sources.sha1"))
        server.expectGetMissing("/repo/org/test/someCoolProject/maven-metadata.xml")
        server.expectPut("/repo/org/test/someCoolProject/maven-metadata.xml", dist.testFile("metadata"))
        server.expectPut("/repo/org/test/someCoolProject/maven-metadata.xml.md5", dist.testFile("metadata.md5"))
        server.expectPut("/repo/org/test/someCoolProject/maven-metadata.xml.sha1", dist.testFile("metadata.sha1"))

        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.pom", dist.testFile("tests.pom"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.pom.md5", dist.testFile("tests.pom.md5"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.pom.sha1", dist.testFile("tests.pom.sha1"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.jar", dist.testFile("tests.jar"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.jar.md5", dist.testFile("tests.md5"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.jar.sha1", dist.testFile("tests.sha1"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0-sources.jar", dist.testFile("tests-sources.jar"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0-sources.jar.md5", dist.testFile("tests-sources.md5"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0-sources.jar.sha1", dist.testFile("tests-sources.sha1"))
        server.expectGetMissing("/repo/org/test/someCoolProject-tests/maven-metadata.xml")
        server.expectPut("/repo/org/test/someCoolProject-tests/maven-metadata.xml", dist.testFile("tests.metadata"))
        server.expectPut("/repo/org/test/someCoolProject-tests/maven-metadata.xml.md5", dist.testFile("tests.metadata.md5"))
        server.expectPut("/repo/org/test/someCoolProject-tests/maven-metadata.xml.sha1", dist.testFile("tests.metadata.sha1"))



        executer.withTasks('uploadArchives').run()
    }

    def MavenRepository repo() {
        new MavenRepository(dist.testFile('mavenRepo'))
    }

}
