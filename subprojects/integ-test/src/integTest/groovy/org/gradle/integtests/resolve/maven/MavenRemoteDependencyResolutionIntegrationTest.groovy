/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.gradle.util.TestFile
import org.junit.Rule

class MavenRemoteDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()
    @Rule public final TestResources resources = new TestResources();

    def canResolveDependenciesFromMultipleMavenRepositories() {
        given:
        List expectedFiles = ['sillyexceptions-1.0.1.jar', 'repotest-1.0.jar', 'testdep-1.0.jar', 'testdep2-1.0.jar',
                'classifier-1.0-jdk15.jar', 'classifier-dep-1.0.jar', 'jaronly-1.0.jar']

        File projectDir = distribution.testDir
        executer.inDirectory(projectDir).withTasks('retrieve').run()
        
        expect:
        expectedFiles.each { new TestFile(projectDir, 'build', it).assertExists() }
    }

    def "can resolve and cache dependencies from HTTP Maven repository"() {
        given:
        requireOwnUserHomeDir()
        server.start()

        def projectA = repo().module('group', 'projectA')
        projectA.publish()

        buildFile << """
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.0'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        
        and:        
        run 'retrieve'
        
        then:
        file('libs').assertHasDescendants('projectA-1.0.jar')
        def snapshot = file('libs/projectA-1.0.jar').snapshot()
        
        when:
        server.resetExpectations()
        and:
        run 'retrieve'
        
        then:
        file('libs/projectA-1.0.jar').assertHasNotChangedSince(snapshot)
    }

    def "does not download source and javadoc artifacts from HTTP Maven repository until required"() {
        given:
        requireOwnUserHomeDir()
        server.start()

        def projectA = repo().module('group', 'projectA', '1.0')
        projectA.artifact(classifier: 'sources')
        projectA.artifact(classifier: 'javadoc')
        projectA.publish()
        def sourceJar = projectA.artifactFile(classifier: 'sources')
        def javadocJar = projectA.artifactFile(classifier: 'javadoc')

        buildFile << """
apply plugin: 'java'
apply plugin: 'eclipse'
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
}
dependencies {
    compile 'group:projectA:1.0'
}
eclipse { classpath { downloadJavadoc = true } }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar']
}
"""
        
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        run 'listJars'

        server.resetExpectations()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0-sources.jar', sourceJar)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0-javadoc.jar', javadocJar)

        run 'eclipseClasspath'
    }

    def "can resolve and cache dependencies from multiple HTTP Maven repositories"() {
        given:
        requireOwnUserHomeDir()
        server.start()

        buildFile << """
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
    maven { url 'http://localhost:${server.port}/repo2' }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.0', 'group:projectB:1.0'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar', 'projectB-1.0.jar']
}
"""

        def projectA = repo().module('group', 'projectA')
        def projectB = repo().module('group', 'projectB')
        projectA.publish()
        projectB.publish()

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)

        // Looks for POM and JAR in repo1 before looking in repo2 (jar is an attempt to handle publication without module descriptor)
        server.expectGetMissing('/repo1/group/projectB/1.0/projectB-1.0.pom')
        server.expectGetMissing('/repo1/group/projectB/1.0/projectB-1.0.jar')
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        run 'listJars'

        server.resetExpectations()
        // No server requests when all jars cached

        run 'listJars'
    }

    def "uses artifactsUrl to resolve artifacts"() {
        given:
        requireOwnUserHomeDir()
        server.start()

        buildFile << """
repositories {
    maven {
        url 'http://localhost:${server.port}/repo1'
        artifactUrls 'http://localhost:${server.port}/repo2'
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.0', 'group:projectB:1.0'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar', 'projectB-1.0.jar']
}
"""

        def projectA = repo().module('group', 'projectA')
        def projectB = repo().module('group', 'projectB')
        projectA.publish()
        projectB.publish()

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGetMissing('/repo1/group/projectB/1.0/projectB-1.0.jar')
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        run 'listJars'
    }

    def "can resolve dependencies from password protected HTTP Maven repository"() {
        given:
        requireOwnUserHomeDir()
        server.start()
        
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
task retrieve(type: Sync) {
    into 'build'
    from configurations.compile
}
"""
        and:
        def module = repo().module('group', 'projectA', '1.2')
        module.publish()

        when:
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.pom', 'username', 'password', module.pomFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', 'username', 'password', module.artifactFile)

        and:
        run 'retrieve'
        
        then:
        file('build').assertHasDescendants('projectA-1.2.jar')
    }

    MavenRepository repo() {
        return new MavenRepository(file('repo'))
    }
}
