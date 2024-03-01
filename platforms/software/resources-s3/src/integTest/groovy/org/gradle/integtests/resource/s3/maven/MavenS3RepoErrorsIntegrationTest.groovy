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

import org.gradle.api.credentials.AwsCredentials
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.resource.s3.AbstractS3DependencyResolutionTest
import org.gradle.integtests.resource.s3.fixtures.MavenS3Module

import static org.gradle.integtests.fixtures.SuggestionsMessages.DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.gradle.integtests.fixtures.SuggestionsMessages.repositoryHint

class MavenS3RepoErrorsIntegrationTest extends AbstractS3DependencyResolutionTest {
    final String artifactVersion = "1.85"
    MavenS3Module module;

    @Override
    String getRepositoryPath() {
        return '/maven/release/'
    }

    def setup() {
        module = mavenS3Repo.module("org.gradle", "test", artifactVersion)
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
    }

    @ToBeFixedForConfigurationCache
    def "should fail with an AWS S3 authentication error"() {
        setup:
        buildFile << mavenAwsRepoDsl()
        when:
        module.pom.expectDownloadAuthenticationError()
        then:
        fails 'retrieve'
        and:
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
                .assertHasCause('Could not resolve org.gradle:test:1.85')
                .assertHasCause("Could not get resource '${module.pom.uri}'.")
                .assertHasCause("The AWS Access Key Id you provided does not exist in our records.")
    }

    @ToBeFixedForConfigurationCache
    def "fails when providing PasswordCredentials with decent error"() {
        setup:
        buildFile << """
repositories {
    maven {
        url "${mavenS3Repo.uri}"
        credentials {
            username "someUserName"
            password "someSecret"
        }
    }
}
"""

        when:
        fails 'retrieve'
        then:
        //TODO would be good to have a reference of the wrong configured repository in the error message
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
                .assertHasCause("Credentials must be an instance of '${AwsCredentials.class.getName()}'.")
    }

    @ToBeFixedForConfigurationCache
    def "fails when no credentials provided"() {
        setup:
        buildFile << """
repositories {
    maven {
        url "${mavenS3Repo.uri}"
    }
}
"""

        when:
        fails 'retrieve'
        then:
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
                .assertHasCause("S3 resource should either specify AwsImAuthentication or provide some AwsCredentials.")

    }

    @ToBeFixedForConfigurationCache
    def "should include resource uri when file not found"() {
        setup:
        buildFile << mavenAwsRepoDsl()
        when:
        module.pom.expectDownloadMissing()
        then:
        fails 'retrieve'

        and:
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause(
                """Could not find org.gradle:test:1.85.
Searched in the following locations:
  - ${module.pom.uri}
Required by:
""")
        failure.assertHasResolutions(repositoryHint("Maven POM"),
            STACKTRACE_MESSAGE,
            DEBUG,
            SCAN,
            GET_HELP)
    }

    @ToBeFixedForConfigurationCache
    def "cannot add invalid authentication types for s3 repo"() {
        given:
        module.publish()

        and:
        buildFile << """
            repositories {
                maven {
                    url "${mavenS3Repo.uri}"
                    authentication {
                        auth(BasicAuthentication)
                    }
                }
            }
        """

        expect:
        fails 'retrieve'
        and:
        failure.assertHasDescription("Execution failed for task ':retrieve'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Authentication scheme 'auth'(BasicAuthentication) is not supported by protocol 's3'")
    }
}
