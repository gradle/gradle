/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

import java.security.MessageDigest

/**
 * Resolves from an S3 repository with {@code AwsImAuthentication} and an AWS SSO profile.
 * The SDK exchanges the cached SSO access token for role credentials, so the test serves
 * the SSO operation from the stub server and requires the S3 requests to be signed with those credentials.
 */
@Issue("https://github.com/gradle/gradle/issues/34049")
class MavenS3SsoAuthenticationIntegrationTest extends AbstractS3DependencyResolutionTest {
    static final String START_URL = "https://gradle-test.awsapps.com/start"
    static final String SSO_REGION = "us-east-1"
    static final String SSO_ACCESS_TOKEN = "cached-sso-access-token"
    static final String ACCOUNT_ID = "123456789012"
    static final String ROLE_NAME = "GradleTestRole"
    static final String ACCESS_KEY_ID = "ASIAGRADLESSOTEST"
    static final String SECRET_ACCESS_KEY = "sso-role-secret"
    static final String SESSION_TOKEN = "sso-role-session-token"

    @Override
    String getRepositoryPath() {
        return '/maven/release/'
    }

    def "resolves with credentials from an AWS SSO profile using #format"() {
        given:
        // The SDK finds ~/.aws through the HOME environment variable
        TestFile home = file("user-home")
        home.file(".aws/config").text = profiles
        home.file(".aws/sso/cache/${sha1(tokenCacheKey)}.json").text = cachedToken
        executer.withEnvironmentVars(HOME: home.absolutePath)
        // A reused daemon keeps the HOME it started with, so start one for this test
        executer.requireIsolatedDaemons()
        executer.withArgument("-Daws.endpointUrlSso=${server.uri}")

        server.allowSsoRoleCredentials(SSO_ACCESS_TOKEN, ACCOUNT_ID, ROLE_NAME, ACCESS_KEY_ID, SECRET_ACCESS_KEY, SESSION_TOKEN)
        // Without this, a failed SSO lookup would fall back to anonymous requests that the stub also serves
        server.requireSignedBy(ACCESS_KEY_ID, SESSION_TOKEN)

        def module = mavenS3Repo.module("org.gradle", "test", "1.0").publish()
        buildFile << """
            repositories {
                maven {
                    url = "${mavenS3Repo.uri}"
                    authentication {
                        awsIm(AwsImAuthentication)
                    }
                }
            }
            configurations { compile }
            dependencies { compile 'org.gradle:test:1.0' }
            task retrieve(type: Sync) {
                from configurations.compile
                into 'libs'
            }
        """

        when:
        module.pom.expectDownload()
        module.artifact.expectDownload()

        then:
        succeeds 'retrieve'
        file('libs/test-1.0.jar').assertIsCopyOf(module.artifactFile)

        where:
        format                   | tokenCacheKey | profiles                 | cachedToken
        "a legacy start URL"     | START_URL     | legacyStartUrlProfile()  | cachedToken([:])
        "an SSO session"         | "gradle-test" | ssoSessionProfile()      | cachedToken(refreshToken: "refresh-token", clientId: "client-id", clientSecret: "client-secret", registrationExpiresAt: "2099-01-01T00:00:00Z")
    }

    private static String legacyStartUrlProfile() {
        """
        [default]
        sso_start_url = ${START_URL}
        sso_region = ${SSO_REGION}
        sso_account_id = ${ACCOUNT_ID}
        sso_role_name = ${ROLE_NAME}
        region = ${SSO_REGION}
        """.stripIndent()
    }

    private static String ssoSessionProfile() {
        """
        [default]
        sso_session = gradle-test
        sso_account_id = ${ACCOUNT_ID}
        sso_role_name = ${ROLE_NAME}
        region = ${SSO_REGION}

        [sso-session gradle-test]
        sso_start_url = ${START_URL}
        sso_region = ${SSO_REGION}
        sso_registration_scopes = sso:account:access
        """.stripIndent()
    }

    // The cache file that `aws sso login` writes, valid long enough that the SDK doesn't try to refresh it
    private static String cachedToken(Map<String, String> extraFields) {
        Map<String, String> fields = [startUrl: START_URL, region: SSO_REGION, accessToken: SSO_ACCESS_TOKEN, expiresAt: "2099-01-01T00:00:00Z"] + extraFields
        "{" + fields.collect { key, value -> "\"${key}\": \"${value}\"" }.join(", ") + "}"
    }

    private static String sha1(String value) {
        MessageDigest.getInstance("SHA-1").digest(value.getBytes("UTF-8")).encodeHex().toString()
    }
}
