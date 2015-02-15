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

import org.gradle.test.fixtures.server.http.TestProxyServer
import org.gradle.test.fixtures.server.s3.MavenS3Module
import org.gradle.util.SetSystemProperties
import org.junit.Rule

class MavenS3ProxiedRepoIntegrationTest extends AbstractS3DependencyResolutionTest {

    @Rule
    TestProxyServer proxyServer = new TestProxyServer(server)
    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties()

    final String artifactVersion = "1.85"

    @Override
    String getRepositoryPath() {
        return '/maven/release/'
    }

    @Override
    def setup() {
        proxyServer.start()
    }

    def "should proxy requests using HTTP system proxy settings"() {
        setup:
        MavenS3Module remoteModule = getMavenS3Repo().module("org.gradle", "test", artifactVersion)
        remoteModule.publish()

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
        remoteModule.pom.expectDownload()
        remoteModule.artifact.expectDownload()

        when:
        executer.withArguments(
                "-Dorg.gradle.s3.endpoint=${s3StubSupport.endpoint.toString()}",
                "-Dhttp.proxyHost=localhost",
                "-Dhttp.proxyPort=${proxyServer.port}",
                "-Dhttp.nonProxyHosts=foo",
                "-Dhttp.proxyUser=proxyUser",
                "-Dhttp.proxyPassword=proxyPassword"
        )

        then:
        succeeds 'retrieve'

        and:
        proxyServer.port != server.port
        proxyServer.requestCount == 2
    }

    def "should not proxy requests when HTTP system proxy settings has a nonProxyHost rule"() {
        setup:
        MavenS3Module remoteModule = getMavenS3Repo().module("org.gradle", "test", artifactVersion)
        remoteModule.publish()

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
        remoteModule.pom.expectDownload()
        remoteModule.artifact.expectDownload()

        when:
        executer.withArguments(
                "-Dorg.gradle.s3.endpoint=${s3StubSupport.endpoint.toString()}",
                "-Dhttp.proxyHost=localhost",
                "-Dhttp.proxyPort=${proxyServer.port}",
                "-Dhttp.nonProxyHosts=localhost",
                "-Dhttp.proxyUser=proxyUser",
                "-Dhttp.proxyPassword=proxyPassword"
        )

        then:
        succeeds 'retrieve'

        and:
        proxyServer.port != server.port
        proxyServer.requestCount == 0
    }
}
