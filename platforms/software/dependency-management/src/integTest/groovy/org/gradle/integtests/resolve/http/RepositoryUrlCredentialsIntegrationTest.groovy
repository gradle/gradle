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

package org.gradle.integtests.resolve.http

import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.gradle.test.fixtures.server.http.AuthScheme

class RepositoryUrlCredentialsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def "does not show credentials embedded in the URL of a #repoType repository when a module is not found at #logLevel log level"() {
        given:
        def repo = repoType == "maven" ? mavenHttpRepo : ivyHttpRepo
        def module = repo.module('group', 'projectA', '1.2')
        declareRepositoryWithUrlCredentials(repoType, repo.uri)

        when:
        if (repoType == "maven") {
            module.pom.expectGetMissing()
        } else {
            module.ivy.expectGetMissing()
        }

        then:
        fails('resolve', *(logLevel == 'info' ? ['--info', '--stacktrace'] : []))

        and:
        failure.assertHasCause("Could not find group:projectA:1.2.")
        failure.assertHasErrorOutput("- ${masked(repo.uri)}/")
        failure.assertNotOutput("s3cret")
        failure.assertNotOutput("someuser")

        where:
        [repoType, logLevel] << [["maven", "ivy"], ["default", "info"]].combinations()
    }

    def "does not show credentials embedded in the URL of a #repoType repository when authentication fails at #logLevel log level"() {
        given:
        def repo = repoType == "maven" ? mavenHttpRepo : ivyHttpRepo
        def module = repo.module('group', 'projectA', '1.2').publish()
        declareRepositoryWithUrlCredentials(repoType, repo.uri)

        when:
        server.authenticationScheme = AuthScheme.BASIC
        if (repoType == "maven") {
            module.pom.allowGetOrHead('username', 'password')
        } else {
            module.ivy.allowGetOrHead('username', 'password')
        }

        then:
        fails('resolve', *(logLevel == 'info' ? ['--info', '--stacktrace'] : []))

        and:
        failure.assertHasCause("Could not resolve group:projectA:1.2.")
        failure.assertHasErrorOutput("Could not get resource '${masked(repo.uri)}/")
        failure.assertNotOutput("s3cret")
        failure.assertNotOutput("someuser")

        where:
        [repoType, logLevel] << [["maven", "ivy"], ["default", "info"]].combinations()
    }

    def "does not show credentials embedded in the URL of a Maven repository when its version listing is broken"() {
        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()
        declareRepositoryWithUrlCredentials("maven", mavenHttpRepo.uri, "group:projectA:1.+")

        when:
        module.rootMetaData.expectGetBroken()

        then:
        fails('resolve', '--info', '--stacktrace')

        and:
        failure.assertHasCause("Could not resolve group:projectA:1.+.")
        failure.assertHasCause("Unable to load Maven meta-data from ${masked(module.rootMetaData.uri)}.")
        failure.assertNotOutput("s3cret")
        failure.assertNotOutput("someuser")
    }

    def "does not show credentials embedded in the URL of an Ivy repository when no version is listed"() {
        given:
        declareRepositoryWithUrlCredentials("ivy", ivyHttpRepo.uri, "group:projectA:1.+")

        when:
        ivyHttpRepo.directoryList('group', 'projectA').expectGetMissing()

        then:
        fails('resolve', '--info', '--stacktrace')

        and:
        failure.assertHasCause("Could not find any matches for group:projectA:1.+ as no versions of group:projectA are available.")
        failure.assertHasErrorOutput("- ${masked(ivyHttpRepo.uri)}/group/projectA/")
        failure.assertNotOutput("s3cret")
        failure.assertNotOutput("someuser")
    }

    def "does not show credentials embedded in the URL of a #repoType repository when it uses an insecure protocol"() {
        given:
        def repo = repoType == "maven" ? mavenHttpRepo : ivyHttpRepo
        // 127.0.0.1 is exempt from the insecure protocol check
        def insecureUri = new URI(repo.uri.toString().replace("127.0.0.1", "localhost"))
        declareRepositoryWithUrlCredentials(repoType, insecureUri)

        when:
        fails('resolve')

        then:
        failure.assertHasCause("Using insecure protocols with repositories, without explicit opt-in, is unsupported.")
        failure.assertHasResolution("Switch ${repoType.capitalize()} repository '${repoType}(${masked(insecureUri)})' to redirect to a secure protocol (like HTTPS) or allow insecure protocols.")
        failure.assertNotOutput("s3cret")
        failure.assertNotOutput("someuser")

        where:
        repoType << ["maven", "ivy"]
    }

    def "does not expose credentials embedded in the URL of a #repoType repository in build operations"() {
        given:
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)
        def repo = repoType == "maven" ? mavenHttpRepo : ivyHttpRepo
        def module = repo.module('group', 'projectA', '1.2').publish()
        def extraLocation = repoType == "maven" ? "artifactUrls" : "artifactPattern"
        declareRepositoryWithUrlCredentials(repoType, repo.uri, "group:projectA:1.2", "${extraLocation} \"\${url}/extra\"")

        when:
        module.allowAll()
        if (repoType == "maven") {
            executer.expectDocumentedDeprecationWarning("The MavenArtifactRepository.artifactUrls(Object...) method has been deprecated. This is scheduled to be removed in Gradle 10. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_maven_artifact_urls")
        }
        succeeds('resolve')

        then:
        def downloads = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloads*.details.location.contains(masked(module.artifact.uri))
        downloads*.displayName.contains("Download ${masked(module.artifact.uri)}".toString())
        def repositories = buildOperations.first(ResolveConfigurationDependenciesBuildOperationType).details.repositories
        repositories*.properties.URL == [masked(repo.uri)]
        repositories*.properties[repoType == "maven" ? "ARTIFACT_URLS" : "ARTIFACT_PATTERNS"].flatten().contains("${masked(repo.uri)}/extra".toString())

        and:
        def trace = file("operations-log.txt").text
        trace.contains(masked(module.artifact.uri))
        !trace.contains("s3cret")
        !trace.contains("someuser")

        where:
        repoType << ["maven", "ivy"]
    }

    private static String masked(URI uri) {
        withUserInfo(uri, "***").toString()
    }

    private static URI withUserInfo(URI uri, String userInfo) {
        new URI(uri.scheme, userInfo, uri.host, uri.port, uri.path, null, null)
    }

    private void declareRepositoryWithUrlCredentials(String repoType, URI uri, String dependency = "group:projectA:1.2", String extraConfiguration = "") {
        def urlWithCredentials = withUserInfo(uri, "someuser:s3cret")
        buildFile << """
            repositories {
                ${repoType} {
                    url = "${urlWithCredentials}"
                    ${extraConfiguration}
                }
            }
            configurations { compile }
            dependencies {
                compile '${dependency}'
            }
            task resolve {
                def files = configurations.compile
                doLast {
                    files.files
                }
            }
        """
    }
}
