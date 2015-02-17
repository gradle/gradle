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

package org.gradle.integtests.resolve.resource.s3

import org.gradle.test.fixtures.server.s3.MavenS3Module

class MavenS3SnapshotRepoIntegrationTest extends AbstractS3DependencyResolutionTest {

    @Override
    String getRepositoryPath() {
        return '/maven/snapshot/'
    }

    def "resolves a maven snapshot module stored in S3"() {
        setup:
        MavenS3Module module = getMavenS3Repo().module("org.gradle", "test", "1.45-SNAPSHOT")
        module.publish()

        buildFile << mavenAwsRepoDsl()
        buildFile << """
configurations { compile }

dependencies{
    compile 'org.gradle:test:1.45-SNAPSHOT'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        expect:
        module.pom.expectDownload()
        module.metaData.expectDownload()
        module.artifact.expectDownload()

        and:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('test-1.45-SNAPSHOT.jar')
    }

    def "resolves a dynamic maven snapshot module stored in S3"() {
        setup:
        MavenS3Module remoteModule = getMavenS3Repo().module("org.gradle", "test", "1.45-SNAPSHOT")
        remoteModule.publish()

        and:
        buildFile << mavenAwsRepoDsl()
        buildFile << """
configurations { compile }


dependencies{
    compile 'org.gradle:test:1.45-SNAPSHOT+'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        expect:
        remoteModule.mavenRootMetaData.expectDownload()
        remoteModule.metaData.expectDownload()
        remoteModule.pom.expectDownload()
        remoteModule.artifact.expectDownload()

        and:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('test-1.45-SNAPSHOT.jar')
    }

    def "should download snapshot artifacts when maven local artifacts are different to remote"() {
        setup:
        String artifactVersion = "1.45-SNAPSHOT"
        MavenS3Module remoteModule = getMavenS3Repo().module("org.gradle", "test", artifactVersion)
        remoteModule.publishWithChangedContent()

        m2Installation.generateGlobalSettingsFile()
        def localModule = m2Installation.mavenRepo().module("org.gradle", "test", artifactVersion).publish()

        buildFile << mavenAwsRepoDsl()
        buildFile << """
configurations { compile }

dependencies{
    compile 'org.gradle:test:$artifactVersion'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
        and:
        remoteModule.metaData.expectDownload()
        remoteModule.pom.expectDownload()
        remoteModule.artifact.expectDownload()

        when:
        using m2Installation
        run 'retrieve'

        then:
        succeeds 'retrieve'

        and:
        localModule.artifactFile.assertIsDifferentFrom(remoteModule.artifactFile)
        localModule.pomFile.assertIsDifferentFrom(remoteModule.pomFile)
        file("libs/test-${artifactVersion}.jar").assertIsCopyOf(remoteModule.artifactFile)
    }
}
