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

package org.gradle.integtests.resource.s3.maven

import org.gradle.integtests.resource.s3.AbstractS3DependencyResolutionTest
import org.gradle.integtests.resource.s3.fixtures.MavenS3Module

class MavenS3SnapshotRepoIntegrationTest extends AbstractS3DependencyResolutionTest {

    String artifactVersion = "1.45-SNAPSHOT"
    MavenS3Module module

    def setup() {
        module = getMavenS3Repo().module("org.gradle", "test", artifactVersion)
    }

    def "resolves a maven snapshot module stored in S3"() {
        setup:
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
        module.publish()

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
        module.rootMetaData.expectDownload()
        module.metaData.expectDownload()
        module.pom.expectDownload()
        module.artifact.expectDownload()

        and:
        succeeds 'retrieve'

        and:
        file('libs').assertHasDescendants('test-1.45-SNAPSHOT.jar')
    }

    def "should download snapshot artifacts when maven local artifacts are different to remote"() {
        setup:
        module.publishWithChangedContent()

        m2.generateGlobalSettingsFile()
        def localModule = m2.mavenRepo().module("org.gradle", "test", artifactVersion).publish()

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
        module.metaData.expectDownload()
        module.pom.expectDownload()
        module.artifact.expectDownload()

        when:
        using m2
        run 'retrieve'

        then:
        succeeds 'retrieve'

        and:
        localModule.artifactFile.assertIsDifferentFrom(module.artifactFile)
        localModule.pomFile.assertIsDifferentFrom(module.pomFile)
        file("libs/test-${artifactVersion}.jar").assertIsCopyOf(module.artifactFile)
    }

    @Override
    String getRepositoryPath() {
        return '/maven/snapshot/'
    }

}
