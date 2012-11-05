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
import org.gradle.test.fixtures.server.HttpServer
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.spockframework.util.TextUtil
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion

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
            repository(url: "${mavenRepo.uri}")
        }
    }
}
"""
        when:
        succeeds 'uploadArchives'

        then:
        def module = mavenRepo.module('group', 'root', 1.0)
        module.assertArtifactsPublished('root-1.0.jar', 'root-1.0.pom')
    }

    @Issue("GRADLE-2456")
    public void generatesSHA1FileWithLeadingZeros() {
        given:
        def module = mavenRepo.module("org.gradle", "publish", "2")
        byte[] jarBytes = [0, 0, 0, 5]
        def artifactFile = file("testfile.bin")
        artifactFile << jarBytes
        def artifactPath = TextUtil.escape(artifactFile.path)
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
    apply plugin:'java'
    apply plugin: 'maven'
    group = "org.gradle"
    version = '2'
    artifacts {
        archives file: file("${artifactPath}")
    }

    uploadArchives {
        repositories {
            mavenDeployer {
                repository(url: "${mavenRepo.uri}")
            }
        }
    }
    """
        when:
        succeeds 'uploadArchives'

        then:
        def shaOneFile = module.moduleDir.file("publish-2.bin.sha1")
        shaOneFile.exists()
        shaOneFile.text == "00e14c6ef59816760e2c9b5a57157e8ac9de4012"
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
            repository(url: "${mavenRepo.uri}")
        }
    }
}
"""
        when:
        succeeds 'uploadArchives'

        then:
        def module = mavenRepo.module('group', 'root', 1.0)
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
            repository(url: uri("${mavenRepo.uri}"))
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
        def module = mavenRepo.module('group', 'root', 1.0)
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
            snapshotRepository(url: "${mavenRepo.uri}")
        }
    }
}
"""

        when:
        succeeds 'uploadArchives'

        then:
        def module = mavenRepo.module('org.gradle', 'test', '1.0-SNAPSHOT')
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
        def module = mavenRepo.module('org.test', 'someCoolProject')
        def moduleDir = module.moduleDir
        moduleDir.mkdirs()

        and:
        expectPublishArtifact(moduleDir, "/repo/org/test/someCoolProject/1.0", "someCoolProject-1.0.pom")
        expectPublishArtifact(moduleDir, "/repo/org/test/someCoolProject/1.0", "someCoolProject-1.0.jar")
        expectPublishArtifact(moduleDir, "/repo/org/test/someCoolProject/1.0", "someCoolProject-1.0-sources.jar")
        server.expectGetMissing("/repo/org/test/someCoolProject/maven-metadata.xml")
        expectPublishArtifact(moduleDir, "/repo/org/test/someCoolProject", "maven-metadata.xml")

        expectPublishArtifact(moduleDir, "/repo/org/test/someCoolProject-tests/1.0", "someCoolProject-tests-1.0.pom")
        expectPublishArtifact(moduleDir, "/repo/org/test/someCoolProject-tests/1.0", "someCoolProject-tests-1.0.jar")
        expectPublishArtifact(moduleDir, "/repo/org/test/someCoolProject-tests/1.0", "someCoolProject-tests-1.0-sources.jar")
        server.expectGetMissing("/repo/org/test/someCoolProject-tests/maven-metadata.xml")
        expectPublishArtifact(moduleDir, "/repo/org/test/someCoolProject-tests", "maven-metadata.xml")

        then:
        succeeds 'uploadArchives'
    }

    def "can publish to an unauthenticated HTTP repository"() {
        given:
        server.start()
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'
group = 'org.test'
version = '1.0'
uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "http://localhost:${server.port}/repo")
        }
    }
}
"""
        when:
        def module = mavenRepo.module('org.test', 'root')
        def moduleDir = module.moduleDir
        moduleDir.mkdirs()
        expectPublishArtifact(moduleDir, "/repo/org/test/root/1.0", "root-1.0.pom")
        expectPublishArtifact(moduleDir, "/repo/org/test/root/1.0", "root-1.0.jar")
        server.expectGetMissing("/repo/org/test/root/maven-metadata.xml")
        expectPublishArtifact(moduleDir, "/repo/org/test/root", "maven-metadata.xml")

        then:
        succeeds 'uploadArchives'

        and:
        module.assertArtifactsPublished('root-1.0.pom', 'root-1.0.jar', 'maven-metadata.xml')
    }

    private def expectPublishArtifact(def moduleDir, def path, def name) {
        server.expectPut("$path/$name", moduleDir.file("$name"))
        server.expectPut("$path/${name}.md5", moduleDir.file("${name}.md5"))
        server.expectPut("$path/${name}.sha1", moduleDir.file("${name}.sha1"))
    }

    @Unroll
    def "can publish to an authenticated HTTP repository using #authScheme auth"() {
        given:
        def username = 'testuser'
        def password = 'password'
        server.start()
        server.expectUserAgent(matchesNameAndVersion("Gradle", GradleVersion.current().version))
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'
group = 'org.test'
version = '1.0'
uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "http://localhost:${server.port}/repo") {
               authentication(userName: "${username}", password: "${password}")
            }
        }
    }
}
"""
        when:
        server.authenticationScheme = authScheme

        and:
        def module = mavenRepo.module('org.test', 'root')
        def moduleDir = module.moduleDir
        moduleDir.mkdirs()
        expectPublishArtifact(moduleDir, "/repo/org/test/root/1.0", "root-1.0.jar", username, password)
        expectPublishArtifact(moduleDir, "/repo/org/test/root/1.0", "root-1.0.pom", username, password)
        server.expectGetMissing("/repo/org/test/root/maven-metadata.xml")
        expectPublishArtifact(moduleDir, "/repo/org/test/root", "maven-metadata.xml", username, password)

        then:
        succeeds 'uploadArchives'

        and:
        module.assertArtifactsPublished('root-1.0.pom', 'root-1.0.jar', 'maven-metadata.xml')

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
        // TODO: Does not work with DIGEST authentication
    }

    private def expectPublishArtifact(def moduleDir, def path, def name, def username, def password) {
        server.expectPut("$path/$name", username, password, moduleDir.file("$name"))
        server.expectPut("$path/${name}.md5", username, password, moduleDir.file("${name}.md5"))
        server.expectPut("$path/${name}.sha1", username, password, moduleDir.file("${name}.sha1"))
    }
}
