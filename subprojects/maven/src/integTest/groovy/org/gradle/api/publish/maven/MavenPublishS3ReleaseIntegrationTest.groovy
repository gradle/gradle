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

class MavenPublishS3ReleaseIntegrationTest extends AbstractIntegrationSpec {

    String mavenVersion = "1.45"
    String projectName = "publishS3Test"
    String bucket = 'tests3bucket'
    String repositoryPath = '/maven/release/'

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    @Rule
    public final S3FileBackedServer server = new S3FileBackedServer(temporaryFolder.getTestDirectory())

    def setup() {
        executer.withArgument('-i')
        executer.withArgument("-D${S3ConnectionProperties.S3_ENDPOINT_PROPERTY}=${server.getUri()}")
    }

    def "can publish a maven release artifact to s3"() {

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
                   url "s3://${bucket}${getRepositoryPath()}"
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
        server.getContents().size() == 9
        assertReleasePublished(server.getContents())
    }

    def "can publish a release with sources"() {
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
        server.getContents().size() == 12
        assertReleasePublished(server.getContents())
    }

    boolean assertReleasePublished(List<File> files) {
        projectName
        String baseExpr = ".*\\/release\\/org\\/gradle\\/${projectName}"
        File rootMetaData = files.find { it.absolutePath ==~ /${baseExpr}\/maven-metadata.xml/ }
        assert rootMetaData
        assert files.find { it.absolutePath ==~ /${baseExpr}\/maven-metadata.xml.md5/ }
        assert files.find { it.absolutePath ==~ /${baseExpr}\/maven-metadata.xml.sha1/ }


        def mainArtifact = files.find { it.absolutePath ==~ /${baseExpr}\\/${mavenVersion}\\/${projectName}-${mavenVersion}\.jar/ }
        assert mainArtifact
        assert files.find { it.absolutePath ==~ /${baseExpr}\\/${mavenVersion}\\/${projectName}-${mavenVersion}\.jar.sha1/ }
        assert files.find { it.absolutePath ==~ /${baseExpr}\\/${mavenVersion}\\/${projectName}-${mavenVersion}\.jar.md5/ }

        assert files.find { it.absolutePath ==~ /${baseExpr}\\/${mavenVersion}\\/${projectName}-${mavenVersion}\.pom/ }
        assert files.find { it.absolutePath ==~ /${baseExpr}\\/${mavenVersion}\\/${projectName}-${mavenVersion}\.pom.md5/ }
        assert files.find { it.absolutePath ==~ /${baseExpr}\\/${mavenVersion}\\/${projectName}-${mavenVersion}\.pom.sha1/ }


        def rootInfo = new XmlSlurper().parse(rootMetaData)
        assert rootInfo.versioning.versions[0] == mavenVersion
        rootInfo.groupId.text() == 'org.gradle'
        rootInfo.version.text() == mavenVersion
        rootInfo.artifactId.text() == projectName
        true
    }
}
