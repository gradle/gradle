/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.integtests.fixtures.MavenRepository
import org.junit.Rule

class MavenPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()

    def "can publish a project with dependency in mapped and unmapped configuration"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'
group = 'group'
version = '1.0'
repositories { mavenCentral() }
configurations { custom }
dependencies {
    custom 'commons-collections:commons-collections:3.2'
    runtime 'commons-collections:commons-collections:3.2'
}
uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri("mavenRepo"))
        }
    }
}
"""
        when:
        succeeds 'uploadArchives'

        then:
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsPublished('root-1.0.jar', 'root-1.0.pom')
    }

    def "can publish a project with no main artifact"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
apply plugin: 'base'
apply plugin: 'maven'

group = 'group'
version = 1.0

task sourceJar(type: Jar) {
    classifier = 'source'
}
artifacts {
    archives sourceJar
}
uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri('mavenRepo'))
        }
    }
}
"""
        when:
        succeeds 'uploadArchives'

        then:
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsPublished('root-1.0-source.jar')
    }

    def "can publish a project with metadata artifacts"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'
group = 'group'
version = 1.0

task signature {
    ext.destFile = file("\$buildDir/signature.sig")
    doLast {
        destFile.text = 'signature'
    }
}

import org.gradle.api.artifacts.maven.MavenDeployment
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

artifacts {
    archives new DefaultPublishArtifact(jar.baseName, "jar.sig", "jar.sig", null, new Date(), signature.destFile, signature)
}

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: uri("mavenRepo"))
            beforeDeployment { MavenDeployment deployment ->
                assert deployment.pomArtifact.file.isFile()
                assert deployment.pomArtifact.name == 'root'
                assert deployment.mainArtifact.file == jar.archivePath
                assert deployment.mainArtifact.name == 'root'
                assert deployment.artifacts.size() == 3
                assert deployment.artifacts.contains(deployment.pomArtifact)
                assert deployment.artifacts.contains(deployment.mainArtifact)

                def pomSignature = file("\${buildDir}/pom.sig")
                pomSignature.text = 'signature'
                deployment.addArtifact new DefaultPublishArtifact(deployment.pomArtifact.name, "pom.sig", "pom.sig", null, new Date(), pomSignature)
            }
        }
    }
}
"""
        when:
        succeeds 'uploadArchives'

        then:
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsPublished('root-1.0.jar', 'root-1.0.jar.sig', 'root-1.0.pom', 'root-1.0.pom.sig')
    }

    def "can publish a snapshot version"() {
        buildFile << """
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

        when:
        succeeds 'uploadArchives'

        then:
        def module = repo().module('org.gradle', 'test', '1.0-SNAPSHOT')
        module.assertArtifactsPublished('test-1.0-SNAPSHOT.jar', 'test-1.0-SNAPSHOT.pom')
    }

    def "can publish multiple deployments with attached artifacts"() {
        given:
        server.start()

        settingsFile << "rootProject.name = 'someCoolProject'"
        buildFile << """
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
                !artifact.name.endsWith("-tests")
            }
            addFilter('tests') {artifact, file ->
                artifact.name.endsWith("-tests")
            }
        }
    }
}
"""
        when:
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.pom", distribution.testFile("pom"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.pom.md5", distribution.testFile("pom.md5"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.pom.sha1", distribution.testFile("pom.sha1"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.jar", distribution.testFile("jar"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.jar.md5", distribution.testFile("jar.md5"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0.jar.sha1", distribution.testFile("jar.sha1"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0-sources.jar", distribution.testFile("sources.jar"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0-sources.jar.md5", distribution.testFile("sources.md5"))
        server.expectPut("/repo/org/test/someCoolProject/1.0/someCoolProject-1.0-sources.jar.sha1", distribution.testFile("sources.sha1"))
        server.expectGetMissing("/repo/org/test/someCoolProject/maven-metadata.xml")
        server.expectPut("/repo/org/test/someCoolProject/maven-metadata.xml", distribution.testFile("metadata"))
        server.expectPut("/repo/org/test/someCoolProject/maven-metadata.xml.md5", distribution.testFile("metadata.md5"))
        server.expectPut("/repo/org/test/someCoolProject/maven-metadata.xml.sha1", distribution.testFile("metadata.sha1"))

        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.pom", distribution.testFile("tests.pom"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.pom.md5", distribution.testFile("tests.pom.md5"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.pom.sha1", distribution.testFile("tests.pom.sha1"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.jar", distribution.testFile("tests.jar"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.jar.md5", distribution.testFile("tests.md5"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0.jar.sha1", distribution.testFile("tests.sha1"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0-sources.jar", distribution.testFile("tests-sources.jar"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0-sources.jar.md5", distribution.testFile("tests-sources.md5"))
        server.expectPut("/repo/org/test/someCoolProject-tests/1.0/someCoolProject-tests-1.0-sources.jar.sha1", distribution.testFile("tests-sources.sha1"))
        server.expectGetMissing("/repo/org/test/someCoolProject-tests/maven-metadata.xml")
        server.expectPut("/repo/org/test/someCoolProject-tests/maven-metadata.xml", distribution.testFile("tests.metadata"))
        server.expectPut("/repo/org/test/someCoolProject-tests/maven-metadata.xml.md5", distribution.testFile("tests.metadata.md5"))
        server.expectPut("/repo/org/test/someCoolProject-tests/maven-metadata.xml.sha1", distribution.testFile("tests.metadata.sha1"))

        then:
        succeeds 'uploadArchives'
    }

    def MavenRepository repo() {
        new MavenRepository(distribution.testFile('mavenRepo'))
    }
}
