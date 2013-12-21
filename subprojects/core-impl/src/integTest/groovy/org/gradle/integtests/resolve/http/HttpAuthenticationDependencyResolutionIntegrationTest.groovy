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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.server.http.HttpServer
import org.hamcrest.Matchers
import spock.lang.Unroll

class HttpAuthenticationDependencyResolutionIntegrationTest extends AbstractDependencyResolutionTest {
    static String badCredentials = "credentials{username 'testuser'; password 'bad'}"

    @Unroll
    public void "can resolve dependencies from #authScheme authenticated HTTP ivy repository"() {
        server.start()
        given:
        def moduleA = ivyRepo().module('group', 'projectA', '1.2').publish()
        ivyRepo().module('group', 'projectB', '2.1').publish()
        ivyRepo().module('group', 'projectB', '2.2').publish()
        def moduleB = ivyRepo().module('group', 'projectB', '2.3').publish()
        ivyRepo().module('group', 'projectB', '3.0').publish()

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
    compile 'group:projectB:2.+'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar','projectB-2.3.jar']
}
"""

        when:
        server.authenticationScheme = authScheme

        and:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', 'username', 'password', moduleA.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', 'username', 'password', moduleA.jarFile)
        server.expectGetDirectoryListing("/repo/group/projectB/", 'username', 'password', moduleB.moduleDir.parentFile)
        server.expectGet("/repo/group/projectB/2.3/ivy-2.3.xml", 'username', 'password', moduleB.ivyFile)
        server.expectGet("/repo/group/projectB/2.3/projectB-2.3.jar", 'username', 'password', moduleB.jarFile)

        then:
        succeeds('listJars')

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
    }

    @Unroll
    public void "can resolve dependencies from #authScheme authenticated HTTP maven repository"() {
        server.start()
        given:
        def moduleA = mavenRepo().module('group', 'projectA', '1.2').publish()
        mavenRepo().module('group', 'projectB', '2.0').publish()
        mavenRepo().module('group', 'projectB', '2.2').publish()
        def moduleB = mavenRepo().module('group', 'projectB', '2.3').publish()
        mavenRepo().module('group', 'projectB', '3.0').publish()
        def moduleC = mavenRepo().module('group', 'projectC', '3.1-SNAPSHOT').publish()
        def moduleD = mavenRepo().module('group', 'projectD', '4-SNAPSHOT').withNonUniqueSnapshots().publish()
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
    compile 'group:projectB:2.+'
    compile 'group:projectC:3.1-SNAPSHOT'
    compile 'group:projectD:4-SNAPSHOT'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar', 'projectB-2.3.jar', 'projectC-3.1-SNAPSHOT.jar', 'projectD-4-SNAPSHOT.jar']
}
"""

        when:
        server.authenticationScheme = authScheme

        and:
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.pom', 'username', 'password', moduleA.pomFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', 'username', 'password', moduleA.artifactFile)
        server.expectGet('/repo/group/projectB/maven-metadata.xml', 'username', 'password', moduleB.rootMetaDataFile)
        server.expectGet('/repo/group/projectB/2.3/projectB-2.3.pom', 'username', 'password', moduleB.pomFile)
        server.expectGet('/repo/group/projectB/2.3/projectB-2.3.jar', 'username', 'password', moduleB.artifactFile)

        server.expectGet('/repo/group/projectC/3.1-SNAPSHOT/maven-metadata.xml', 'username', 'password', moduleC.metaDataFile)
        server.expectGet("/repo/group/projectC/3.1-SNAPSHOT/projectC-${moduleC.getPublishArtifactVersion()}.pom", 'username', 'password', moduleC.pomFile)
        server.expectGet("/repo/group/projectC/3.1-SNAPSHOT/projectC-${moduleC.getPublishArtifactVersion()}.jar", 'username', 'password', moduleC.artifactFile)

        server.expectGet('/repo/group/projectD/4-SNAPSHOT/maven-metadata.xml', 'username', 'password', moduleD.metaDataFile)
        server.expectGet("/repo/group/projectD/4-SNAPSHOT/projectD-4-SNAPSHOT.pom", 'username', 'password', moduleD.pomFile)
        server.expectGet("/repo/group/projectD/4-SNAPSHOT/projectD-4-SNAPSHOT.jar", 'username', 'password', moduleD.artifactFile)

        then:
        succeeds('listJars')

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
    }

    @Unroll
    def "reports failure resolving with #credsName credentials from #authScheme authenticated HTTP ivy repository"() {
        server.start()
        given:
        def module = ivyRepo().module('group', 'projectA', '1.2').publish()
        when:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
   repositories {
       ivy {
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
        server.allowGetOrHead('/repo/group/projectA/1.2/ivy-1.2.xml', 'username', 'password', module.ivyFile)

        then:
        fails 'listJars'

        and:
        failure
            .assertHasDescription('Execution failed for task \':listJars\'.')
            .assertResolutionFailure(':compile')
            .assertThatCause(Matchers.containsString('Received status code 401 from server: Unauthorized'))

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST, HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
        credsName << ['empty', 'empty', 'bad', 'bad']
        creds << ['', '', badCredentials, badCredentials]
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
        server.allowGetOrHead('/repo/group/projectA/1.2/projectA-1.2.pom', 'username', 'password', module.pomFile)

        then:
        fails 'listJars'

        and:
        failure
            .assertHasDescription('Execution failed for task \':listJars\'.')
            .assertResolutionFailure(':compile')
            .assertThatCause(Matchers.containsString('Received status code 401 from server: Unauthorized'))

        where:
        authScheme << [HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST, HttpServer.AuthScheme.BASIC, HttpServer.AuthScheme.DIGEST]
        credsName << ['empty', 'empty', 'bad', 'bad']
        creds << ['', '', badCredentials, badCredentials]
    }
}
