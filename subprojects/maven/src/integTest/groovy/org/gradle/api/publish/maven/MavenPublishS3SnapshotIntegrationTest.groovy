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


package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.server.s3.S3FileBackedServer
import org.junit.Rule

class MavenPublishS3SnapshotIntegrationTest extends AbstractIntegrationSpec {

    String mavenVersion = "1.45-SNAPSHOT"
    String projectName = "publishS3Test"
    String bucket = 'tests3bucket'
    String repositoryPath = '/maven/snapshot/'

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    @Rule
    public final S3FileBackedServer server = new S3FileBackedServer(temporaryFolder.getTestDirectory())

    def setup() {
        executer.withArgument('-i')
        executer.withArgument("-D${S3ConnectionProperties.S3_ENDPOINT_PROPERTY}=${server.getUri()}")
    }

    def "can publish and re-publish a maven snapshot to s3"() {

        when:
        settingsFile << "rootProject.name = '${projectName}'"

        buildFile << """
    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = "org.gradle"
    version = '${mavenVersion}'

    publishing {
        repositories {
                maven {
                   url "s3://${bucket}${repositoryPath}"
                    credentials(AwsCredentials) {
                        accessKey "someKey"
                        secretKey "someSecret"
                    }
                }
            }
        publications {
            pub(MavenPublication) {
                from components.java
            }
        }
    }
    """

        then:
        succeeds 'publish'

        and:
        server.getContents().size() == 12
        assertSnapshotPublished(server.getContents(), 1)

        then:
        executer.withArgument('-i')
        executer.withArgument("-D${S3ConnectionProperties.S3_ENDPOINT_PROPERTY}=${server.getUri()}")
        succeeds('clean', 'publish')

        and:
        server.getContents().size() == 18
        assertSnapshotPublished(server.getContents(), 2)
    }

    def "should publish a snapshot with sources"() {
        when:
        settingsFile << "rootProject.name = '${projectName}'"

        buildFile << """
    apply plugin: 'java'
    apply plugin: 'maven-publish'

    group = "org.gradle"
    version = '${mavenVersion}'

    task sourceJar(type: Jar) {
        from sourceSets.main.allJava
    }

    publishing {
        repositories {
                maven {
                   url "s3://${getBucket()}${repositoryPath}"
                    credentials(AwsCredentials) {
                        accessKey "someKey"
                        secretKey "someSecret"
                    }
                }
            }
        publications {
            pub(MavenPublication) {
                from components.java

                artifact sourceJar {
                    classifier "sources"
                }
            }
        }
    }
    """

        then:
        succeeds 'publish'

        and:
        server.getContents().size() == 15
        assertSnapshotPublished(server.getContents(), 1)
    }

    boolean assertSnapshotPublished(List<File> files, int times = 1) {

        String baseExpr = '.*\\/snapshot\\/org\\/gradle\\/publishS3Test'
        String snapshotArtifactExpr = "\\/$mavenVersion\\/publishS3Test-1.45-\\d{8}\\.\\d{6}"
        File rootMetaData = files.find { it.absolutePath ==~ /${baseExpr}\/maven-metadata.xml/ }
        assert rootMetaData
        assert files.find { it.absolutePath ==~ /${baseExpr}\/maven-metadata.xml.md5/ }
        assert files.find { it.absolutePath ==~ /${baseExpr}\/maven-metadata.xml.sha1/ }

        File snapshotMetaData = files.find { it.absolutePath ==~ /${baseExpr}\/$mavenVersion\/maven-metadata.xml/ }
        assert snapshotMetaData

        assert files.find { it.absolutePath ==~ /${baseExpr}\/$mavenVersion\/maven-metadata.xml.md5/ }
        assert files.find { it.absolutePath ==~ /${baseExpr}\/$mavenVersion\/maven-metadata.xml.sha1/ }

        def mainArtifact = files.find { it.absolutePath ==~ /${baseExpr}${snapshotArtifactExpr}-${times}\.jar/ }
        assert mainArtifact
        assert files.find { it.absolutePath ==~ /${baseExpr}${snapshotArtifactExpr}-${times}\.jar.sha1/ }
        assert files.find { it.absolutePath ==~ /${baseExpr}${snapshotArtifactExpr}-${times}\.jar.md5/ }

        assert files.find { it.absolutePath ==~ /${baseExpr}${snapshotArtifactExpr}-${times}\.pom/ }
        assert files.find { it.absolutePath ==~ /${baseExpr}${snapshotArtifactExpr}-${times}\.pom.sha1/ }
        assert files.find { it.absolutePath ==~ /${baseExpr}${snapshotArtifactExpr}-${times}\.pom.md5/ }

        //Currently maven 2x metadata model
        //http://maven.apache.org/ref/3.0.3/maven-repository-metadata/apidocs/org/apache/maven/artifact/repository/metadata/Versioning.html
        //https://maven.apache.org/ref/2.2.1/maven-repository-metadata/apidocs/org/apache/maven/artifact/repository/metadata/Versioning.html
        def rootInfo = new XmlSlurper().parse(rootMetaData)
        assert rootInfo.versioning.versions[0] == mavenVersion

        def snapshotInfo = new XmlSlurper().parse(snapshotMetaData)
        def matcher = (mainArtifact.getName() =~ /.*-(\d{8})\.(\d{6})-(\d)\.jar/)
        def date = matcher[0][1]
        def time = matcher[0][2]
        def buildNum = matcher[0][3]

        assert buildNum == "$times"

        snapshotInfo.groupId.text() == "org.gradle"
        snapshotInfo.artifactId.text() == "publishS3Test"
        snapshotInfo.version.text() == mavenVersion
        snapshotInfo.versioning.snapshot.timestamp.text() == "${date}.${time}"
        snapshotInfo.versioning.snapshot.buildNumber.text() == buildNum
        snapshotInfo.versioning.lastUpdated.text() == "${date}.${time}"
        true
    }
}
