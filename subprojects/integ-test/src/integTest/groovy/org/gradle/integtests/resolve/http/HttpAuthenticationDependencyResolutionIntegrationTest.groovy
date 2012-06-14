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
package org.gradle.integtests.resolve.http

import org.gradle.integtests.fixtures.HttpServer
import org.hamcrest.Matchers
import spock.lang.Unroll
import org.gradle.integtests.resolve.AbstractDependencyResolutionTest

class HttpAuthenticationDependencyResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    static String badCredentials = "credentials{username 'testuser'; password 'bad'}"

    @Unroll
    public void "can resolve dependencies from #authScheme authenticated HTTP ivy repository"() {
        server.start()
        given:
        def module = ivyRepo().module('group', 'projectA', '1.2').publish()

        and:
        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}/repo"

        credentials {
            password 'password'
            username 'username'
        }
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        when:
        server.authenticationScheme = authScheme

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', 'username', 'password', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', 'username', 'password', module.jarFile)

        then:
        succeeds('listJars')

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
    }

    @Unroll
    public void "can resolve dependencies from #authScheme authenticated HTTP maven repository"() {
        server.start()
        given:
        def module = mavenRepo().module('group', 'projectA', '1.2').publish()

        and:
        buildFile << """
repositories {
    maven {
        url "http://localhost:${server.port}/repo"

        credentials {
            password 'password'
            username 'username'
        }
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        when:
        server.authenticationScheme = authScheme

        and:
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.pom', 'username', 'password', module.pomFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', 'username', 'password', module.artifactFile)

        then:
        succeeds('listJars')

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
    }


    @Unroll
    def "reports failure resolving with #credsName credentials from #authScheme authenticated HTTP maven repository"() {
        given:
        server.start()

        and:
        def module = mavenRepo().module('group', 'projectA', '1.2').publish()

        when:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
 repositories {
     maven {
         url "http://localhost:${server.port}/repo"
        $creds
     }
 }
 configurations { compile }
 dependencies {
     compile 'group:projectA:1.2'
 }
 task listJars << {
     assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
 }
 """

        and:
        server.authenticationScheme = authScheme
        server.allowGet('/repo/group/projectA/1.2/projectA-1.2.pom', 'username', 'password', module.pomFile)

        then:
        fails 'listJars'

        and:
        failure.assertHasDescription('Execution failed for task \':listJars\'.')
        failure.assertHasCause('Could not resolve all dependencies for configuration \':compile\'.')
        failure.assertThatCause(Matchers.containsString('Received status code 401 from server: Unauthorized'))

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST, HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
        credsName << ['empty', 'empty' , 'bad', 'bad']
        creds << ['', '', badCredentials, badCredentials]
    }
}
