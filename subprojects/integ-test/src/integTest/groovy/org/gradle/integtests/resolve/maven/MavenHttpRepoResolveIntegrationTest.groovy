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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.ProgressLoggingFixture
import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.junit.Rule

class MavenHttpRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Rule ProgressLoggingFixture progressLogging

    def "can resolve and cache dependencies from HTTP Maven repository"() {
        given:
        server.start()

        def projectB = mavenRepo().module('group', 'projectB').publish()
        def projectA = mavenRepo().module('group', 'projectA').dependsOn('projectB').publish()

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
        progressLogging.withProgressLogging(getExecuter())
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar', 'projectB-1.0.jar')
        def snapshot = file('libs/projectA-1.0.jar').snapshot()

        and:
        progressLogging.downloadProgressLogged("http://localhost:${server.port}/repo1/group/projectA/1.0/projectA-1.0.pom")
        progressLogging.downloadProgressLogged("http://localhost:${server.port}/repo1/group/projectA/1.0/projectA-1.0.jar")
        progressLogging.downloadProgressLogged("http://localhost:${server.port}/repo1/group/projectB/1.0/projectB-1.0.pom")
        progressLogging.downloadProgressLogged("http://localhost:${server.port}/repo1/group/projectB/1.0/projectB-1.0.jar")

        when:
        server.resetExpectations()
        progressLogging.resetExpectations()
        and:
        run 'retrieve'

        then:
        file('libs/projectA-1.0.jar').assertHasNotChangedSince(snapshot)
        progressLogging.noProgressLogged()
    }

    def "can resolve and cache artifact-only dependencies from an HTTP Maven repository"() {
        server.start()
        given:
        def module = mavenRepo().module('group', 'projectA', '1.2')
        module.publish()

        and:
        buildFile << """
repositories {
    maven { url "http://localhost:${server.port}/repo1" }
    maven { url "http://localhost:${server.port}/repo2" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2@jar' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        when:
        // TODO: Should meta-data be fetched for an artifact-only dependency?
        server.expectGetMissing('/repo1/group/projectA/1.2/projectA-1.2.pom')
        server.expectHeadMissing('/repo1/group/projectA/1.2/projectA-1.2.jar')

        server.expectGet('/repo2/group/projectA/1.2/projectA-1.2.pom', module.pomFile)
        server.expectGet('/repo2/group/projectA/1.2/projectA-1.2.jar', module.artifactFile)

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()
        // No extra calls for cached dependencies

        then:
        succeeds('listJars')
    }

    def "does not download source and javadoc artifacts from HTTP Maven repository until required"() {
        given:
        server.start()

        def projectA = mavenRepo().module('group', 'projectA', '1.0')
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

        when:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        then:
        succeeds 'listJars'

        when:
        server.resetExpectations()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0-sources.jar', sourceJar)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0-javadoc.jar', javadocJar)

        then:
        succeeds 'eclipseClasspath'
    }

    def "only attempts to download missing artifacts from HTTP Maven repository once"() {
        given:
        server.start()

        def projectA = mavenRepo().module('group', 'projectA', '1.0')
        projectA.publish()

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

        when:
        server.resetExpectations()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0-sources.jar')
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0-javadoc.jar')

        then:
        succeeds 'eclipseClasspath'

        when:
        server.resetExpectations()

        then:
        succeeds 'eclipseClasspath'
    }

    def "can resolve and cache dependencies from multiple HTTP Maven repositories"() {
        given:
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

        def projectA = mavenRepo().module('group', 'projectA').publish()
        def projectB = mavenRepo().module('group', 'projectB').publish()

        when:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)

        // Looks for POM and JAR in repo1 before looking in repo2 (jar is an attempt to handle publication without module descriptor)
        server.expectGetMissing('/repo1/group/projectB/1.0/projectB-1.0.pom')
        server.expectHeadMissing('/repo1/group/projectB/1.0/projectB-1.0.jar')
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeeds 'listJars'

        when:
        server.resetExpectations()
        // No server requests when all jars cached

        then:
        succeeds 'listJars'
    }

    def "uses artifactsUrl to resolve artifacts"() {
        given:
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

        def projectA = mavenRepo().module('group', 'projectA')
        def projectB = mavenRepo().module('group', 'projectB')
        projectA.publish()
        projectB.publish()

        when:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGetMissing('/repo1/group/projectB/1.0/projectB-1.0.jar')
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        then:
        succeeds 'listJars'
    }

    def "can resolve and cache dependencies from HTTP Maven repository with invalid settings.xml"() {
        given:
        server.start()

        def projectB = mavenRepo().module('group', 'projectB').publish()
        def projectA = mavenRepo().module('group', 'projectA').dependsOn('projectB').publish()

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

        def m2Home = file("M2_REPO")
        def settingsFile = m2Home.file("conf/settings.xml")
        settingsFile << "invalid content... blabla"

        when:
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        and:

        executer.withEnvironmentVars(M2_HOME: m2Home.absolutePath)
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0.jar', 'projectB-1.0.jar')
        def snapshot = file('libs/projectA-1.0.jar').snapshot()

        when:
        server.resetExpectations()
        and:
        run 'retrieve'

        then:
        file('libs/projectA-1.0.jar').assertHasNotChangedSince(snapshot)
    }
}