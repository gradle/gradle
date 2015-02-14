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
import org.gradle.util.TextUtil

class MavenS3RepoErrorsIntegrationTest extends AbstractS3DependencyResolutionTest {
    final String artifactVersion = "1.85"

    @Override
    String getRepositoryPath() {
        return '/maven/release/'
    }

    def "should fail with an AWS S3 authentication error"() {
        setup:
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
        when:
        s3StubSupport.stubGetFileAuthFailure("/${getBucket()}/maven/release/org/gradle/test/1.85/test-1.85.pom")
        then:
        fails 'retrieve'
        and:
        failure.assertHasDescription("Could not resolve all dependencies for configuration ':compile'.")
                .assertHasCause('Could not resolve org.gradle:test:1.85')
                .assertHasCause("Could not get s3 resource: [s3://tests3bucket/maven/release/org/gradle/test/1.85/test-1.85.pom]. " +
                "The AWS Access Key Id you provided does not exist in our records.")
    }

    def "should include resource uri when file not found"() {
        setup:
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
        when:
        s3StubSupport.stubFileNotFound("/${getBucket()}/maven/release/org/gradle/test/1.85/test-1.85.pom")
        s3StubSupport.stubMetaDataMissing("/${getBucket()}/maven/release/org/gradle/test/1.85/test-1.85.jar")
        then:
        fails 'retrieve'

        and:
        failure.assertHasDescription("Could not resolve all dependencies for configuration ':compile'.")
                errorOutput.contains(TextUtil.toPlatformLineSeparators(
"""Could not find org.gradle:test:1.85.
Searched in the following locations:
    s3://${bucket}/maven/release/org/gradle/test/1.85/test-1.85.pom
    s3://${bucket}/maven/release/org/gradle/test/1.85/test-1.85.jar"""))
    }
}
