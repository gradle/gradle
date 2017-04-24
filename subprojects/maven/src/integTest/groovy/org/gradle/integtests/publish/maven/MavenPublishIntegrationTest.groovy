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
import org.apache.commons.lang.RandomStringUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.maven.MavenLocalRepository
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.spockframework.util.TextUtil
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion
import static org.hamcrest.core.StringContains.containsString

class MavenPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()

    def setup(){
        using m2
    }

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
    custom 'commons-collections:commons-collections:3.2.2'
    runtime 'commons-collections:commons-collections:3.2.2'
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
        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertArtifactsPublished('root-1.0.jar', 'root-1.0.pom')
    }

    def "upload status is logged on on info level"() {
        given:
        def resourceFile = file("src/main/resources/testfile.properties")
        resourceFile << RandomStringUtils.random(5000)
        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'
group = 'group'
version = '1.0'

uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "${mavenRepo.uri}")
        }
    }
}
"""
        when:
        executer.withArgument("-i")
        succeeds 'uploadArchives'

        then:
        output.contains("Uploading: group/root/1.0/root-1.0.jar to repository remote at ${mavenRepo.uri.toString()[0..-2]}")
        output.contains("Transferring 12K from remote")
        output.contains("Uploaded 12K")
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
        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.assertArtifactsPublished('root-1.0.pom', 'root-1.0-source.jar')
    }

    def "can replace artifacts with same coordinates"() {
        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'

group = 'group'
version = 1.0

task replacementJar(type: Jar) {
    baseName = 'root'
    destinationDir = file("\${buildDir}/replacements")
    manifest.attributes foo: "bar"
}
artifacts {
    archives replacementJar // Has same coordinates as main jar
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
        def libs = file("build/libs")
        libs.assertHasDescendants('root-1.0.jar')
        def replacements = file("build/replacements")
        replacements.assertHasDescendants('root-1.0.jar')

        def module = mavenRepo.module('group', 'root', '1.0')
        module.assertPublished()
        module.assertArtifactsPublished('root-1.0.pom', 'root-1.0.jar')

        module.getArtifactFile().assertIsCopyOf(replacements.file('root-1.0.jar'))
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
        destFile.parentFile.mkdirs()
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
        def module = mavenRepo.module('group', 'root', '1.0')
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
        module.assertArtifactsPublished("maven-metadata.xml", "test-${module.publishArtifactVersion}.jar", "test-${module.publishArtifactVersion}.pom")

        and:
        module.parsedPom.version == '1.0-SNAPSHOT'
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

    def "can publish to custom maven local repo defined in settings.xml"() {
        given:
        def localM2Repo = m2.mavenRepo()
        def customLocalRepo = new MavenLocalRepository(file("custom-maven-local"))
        m2.generateUserSettingsFile(customLocalRepo)

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'
        """

        when:
        args "-i"
        succeeds 'install'

        then:
        !localM2Repo.module("group", "root", "1.0").artifactFile(type: "jar").exists()
        customLocalRepo.module("group", "root", "1.0").assertPublishedAsJavaModule()
    }

    @Unroll
    def "can publish to an authenticated HTTP repository using #authScheme auth"() {
        given:
        def username = 'testuser'
        def password = 'password'
        server.start()
        server.expectUserAgent(matchesNameAndVersion("Gradle", GradleVersion.current().version))
        def repo = new MavenHttpRepository(server, mavenRepo)

        settingsFile << "rootProject.name = 'root'"
        buildFile << """
apply plugin: 'java'
apply plugin: 'maven'
group = 'org.test'
version = '1.0'
uploadArchives {
    repositories {
        mavenDeployer {
            repository(url: "${repo.uri}") {
               authentication(userName: "${username}", password: "${password}")
            }
        }
    }
}
"""
        when:
        server.authenticationScheme = authScheme

        and:
        def module = repo.module('org.test', 'root')
        module.artifact.expectPut(username, password)
        module.artifact.sha1.expectPut(username, password)
        module.artifact.md5.expectPut(username, password)
        module.pom.expectPut(username, password)
        module.pom.sha1.expectPut(username, password)
        module.pom.md5.expectPut(username, password)
        module.rootMetaData.expectGetMissing()
        module.rootMetaData.expectPut(username, password)
        module.rootMetaData.sha1.expectPut(username, password)
        module.rootMetaData.md5.expectPut(username, password)

        then:
        succeeds 'uploadArchives'

        where:
        authScheme << [AuthScheme.BASIC, AuthScheme.DIGEST]
        // TODO: Does not work with DIGEST authentication
    }

    @Issue('GRADLE-3272')
    def "can publish to custom maven local repo defined with system property"() {
        given:
        def localM2Repo = m2.mavenRepo()
        def customLocalRepo = mavenLocal("customMavenLocal")
        executer.beforeExecute(m2)

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'
        """

        when:
        args "-Dmaven.repo.local=${customLocalRepo.rootDir.getAbsolutePath()}"
        succeeds 'install'

        then:
        !localM2Repo.module("group", "root", "1.0").artifactFile(type: "jar").exists()
        customLocalRepo.module("group", "root", "1.0").assertPublishedAsJavaModule()
    }

    @Issue('GRADLE-1574')
    def "can publish pom with wildcard exclusions for non-transitive dependencies"() {
        given:
        def localM2Repo = m2.mavenRepo()
        executer.beforeExecute(m2)

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            dependencies {
                compile ('commons-collections:commons-collections:3.2.2') { transitive = false }
            }
        """

        when:
        succeeds 'install'

        then:
        def pom = localM2Repo.module("group", "root", "1.0").parsedPom
        def exclusions = pom.scopes.compile.dependencies['commons-collections:commons-collections:3.2.2'].exclusions
        exclusions.size() == 1 && exclusions[0].groupId=='*' && exclusions[0].artifactId=='*'
    }

    def "dependencies de-duplication uses a 0 priority for unmapped configurations"() {
        given:
        def localM2Repo = m2.mavenRepo()
        executer.beforeExecute(m2)

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            configurations {
                unmapped
                compile {
                    extendsFrom unmapped
                }
            }

            dependencies {
                unmapped('ch.qos.logback:logback-classic:1.1.7') {
                    exclude group: 'ch.qos.logback', module: 'logback-core'
                }
                compile('ch.qos.logback:logback-classic:1.1.5') {
                    exclude group: 'org.slf4j', module: 'slf4j-api'
                }
            }
        """.stripIndent()

        when:
        run 'install'

        then:
        def pom = localM2Repo.module("group", "root", "1.0").parsedPom
        pom.scopes.compile.assertDependsOn 'ch.qos.logback:logback-classic:1.1.5'
        def exclusions = pom.scopes.compile.expectDependency('ch.qos.logback:logback-classic:1.1.5').exclusions
        exclusions.size() == 1
        exclusions[0].groupId == 'org.slf4j'
        exclusions[0].artifactId == 'slf4j-api'
        pom.scopes.provided == null
        pom.scopes.runtime == null
        pom.scopes.test == null
    }

    def "dependency de-duplication takes custom configuration to scope mapping into account"() {
        given:
        def localM2Repo = m2.mavenRepo()
        executer.beforeExecute(m2)

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            configurations {
                unmapped
                compile {
                    extendsFrom unmapped
                }
            }
            conf2ScopeMappings.addMapping(400, configurations.unmapped, 'compile')

            dependencies {
                unmapped('ch.qos.logback:logback-classic:1.1.7') {
                    exclude group: 'ch.qos.logback', module: 'logback-core'
                }
                compile('ch.qos.logback:logback-classic:1.1.5') {
                    exclude group: 'org.slf4j', module: 'slf4j-api'
                }
            }
        """.stripIndent()

        when:
        run 'install'

        then:
        def pom = localM2Repo.module("group", "root", "1.0").parsedPom
        pom.scopes.compile.assertDependsOn 'ch.qos.logback:logback-classic:1.1.7'
        def exclusions = pom.scopes.compile.expectDependency('ch.qos.logback:logback-classic:1.1.7').exclusions
        exclusions.size() == 1
        exclusions[0].groupId == 'ch.qos.logback'
        exclusions[0].artifactId == 'logback-core'
        pom.scopes.provided == null
        pom.scopes.runtime == null
        pom.scopes.test == null
    }

    @Issue('GRADLE-3494')
    def "dependencies de-duplication handles null versions"() {
        given:
        def localM2Repo = m2.mavenRepo()
        executer.beforeExecute(m2)

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            dependencies {
                compile('ch.qos.logback:logback-classic:1.1.5') {
                    exclude group: 'org.slf4j', module: 'slf4j-api'
                }
                compile('ch.qos.logback:logback-classic') {
                    exclude group: 'ch.qos.logback', module: 'logback-core'
                }
            }
        """.stripIndent()

        when:
        run 'install'

        then:
        def pom = localM2Repo.module("group", "root", "1.0").parsedPom
        pom.scopes.compile.assertDependsOn 'ch.qos.logback:logback-classic:1.1.5'
        def exclusions = pom.scopes.compile.expectDependency('ch.qos.logback:logback-classic:1.1.5').exclusions
        exclusions.size() == 1
        exclusions[0].groupId == 'org.slf4j'
        exclusions[0].artifactId == 'slf4j-api'
        pom.scopes.provided == null
        pom.scopes.runtime == null
        pom.scopes.test == null
    }

    @Issue('GRADLE-3496')
    def "dependencies are de-duplicated using the higher version on the same scope and exclusions from the higher version"() {
        given:
        def localM2Repo = m2.mavenRepo()
        executer.beforeExecute(m2)

        and:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: 'maven'
            apply plugin: 'java'

            group = 'group'
            version = '1.0'

            dependencies {
                compile('ch.qos.logback:logback-classic:1.1.5') {
                    exclude group: 'org.slf4j', module: 'slf4j-api'
                }
                compile('ch.qos.logback:logback-classic:1.1.7') {
                    exclude group: 'ch.qos.logback', module: 'logback-core'
                }
            }
        """.stripIndent()

        when:
        run 'install'

        then:
        def pom = localM2Repo.module("group", "root", "1.0").parsedPom
        pom.scopes.compile.assertDependsOn 'ch.qos.logback:logback-classic:1.1.7'
        def exclusions = pom.scopes.compile.expectDependency('ch.qos.logback:logback-classic:1.1.7').exclusions
        exclusions.size() == 1
        exclusions[0].groupId == 'ch.qos.logback'
        exclusions[0].artifactId == 'logback-core'
        pom.scopes.provided == null
        pom.scopes.runtime == null
        pom.scopes.test == null
    }

    def "fails gracefully if trying to publish a directory with Maven"() {

        given:
        file('someDir/a.txt') << 'some text'
        buildFile << """

        apply plugin: 'base'
        apply plugin: 'maven'

        uploadArchives {
            repositories {
                mavenDeployer {
                    repository(url: "${mavenRepo.uri.toURL()}")
                }
            }
        }

        configurations {
            archives
        }

        artifacts {
            archives file("someDir")
        }

        """

        when:
        fails 'uploadArchives'

        then:
        failure.assertHasCause "Could not publish configuration 'archives'"
        failure.assertThatCause(containsString('Cannot publish a directory'))
    }

    @Issue("gradle/gradle#1641")
    def "can publish a new version of a module already present in the target repository"() {
        given:
        server.start()
        def mavenRemoteRepo = new MavenHttpRepository(server, "/repo", mavenRepo)
        def group = 'org.gradle'
        def name = 'publish'

        and:
        settingsFile << "rootProject.name = '$name'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven'
            group = '$group'
            uploadArchives {
                repositories {
                    mavenDeployer {
                        repository(url: "${mavenRemoteRepo.uri}")
                    }
                }
            }
        """.stripIndent()

        and:
        def module1 = mavenRemoteRepo.module(group, name, '1')
        module1.artifact.expectPut()
        module1.artifact.sha1.expectPut()
        module1.artifact.md5.expectPut()
        module1.rootMetaData.expectGetMissing()
        module1.rootMetaData.expectPut()
        module1.rootMetaData.sha1.expectPut()
        module1.rootMetaData.md5.expectPut()
        module1.pom.expectPut()
        module1.pom.sha1.expectPut()
        module1.pom.md5.expectPut()

        when:
        succeeds 'uploadArchives', '-Pversion=1'

        then:
        module1.rootMetaData.verifyChecksums()
        module1.rootMetaData.versions == ["1"]

        and:
        def module2 = mavenRemoteRepo.module(group, name, '2')
        module2.artifact.expectPut()
        module2.artifact.sha1.expectPut()
        module2.artifact.md5.expectPut()
        module2.pom.expectPut()
        module2.pom.sha1.expectPut()
        module2.pom.md5.expectPut()

        and:
        module2.rootMetaData.expectGet()
        module2.rootMetaData.sha1.expectGet()
        module2.rootMetaData.expectPut()
        module2.rootMetaData.sha1.expectPut()
        module2.rootMetaData.md5.expectPut()

        when:
        succeeds 'uploadArchives', '-Pversion=2'

        then:
        module2.rootMetaData.verifyChecksums()
        module2.rootMetaData.versions == ["1", "2"]
    }
}
