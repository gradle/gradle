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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.ProgressLoggingFixture
import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.junit.Rule

class IvyHttpRepoResolveIntegrationTest extends AbstractDependencyResolutionTest {

    @Rule ProgressLoggingFixture progressLogger

    public void "can resolve and cache dependencies from an HTTP Ivy repository"() {
        server.start()
        given:
        def module = ivyRepo().module('group', 'projectA', '1.2').publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""
        when:
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)
        and:
        progressLogger.withProgressLogging(executer, module.jarFile)
        then:
        succeeds 'listJars'
        progressLogger.downloadProgressLogged("http://localhost:${server.port}/repo/group/projectA/1.2/projectA-1.2.jar")
        when:
        server.resetExpectations()
        progressLogger.resetExpectations()
        then:
        succeeds 'listJars'
        progressLogger.noProgressLogged()
    }

    public void "can resolve and cache artifact-only dependencies from an HTTP Ivy repository"() {
        server.start()
        given:
        def module = ivyRepo().module('group', 'projectA', '1.2').publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo" }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2@jar' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""


        when:
        // TODO: Should meta-data be fetched for an artifact-only dependency?
        server.expectGet('/repo/group/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds('listJars')

        when:
        server.resetExpectations()
        // No extra calls for cached dependencies

        then:
        succeeds('listJars')
    }

    public void "can resolve and cache dependencies from multiple HTTP Ivy repositories"() {
        server.start()
        given:
        def repo = ivyRepo()
        def moduleA = repo.module('group', 'projectA').publish()
        def moduleB = repo.module('group', 'projectB').publish()
        def moduleC = repo.module('group', 'projectC').publish()

        and:
        buildFile << """
repositories {
    ivy { url "http://localhost:${server.port}/repo1" }
    ivy { url "http://localhost:${server.port}/repo2" }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.0', 'group:projectB:1.0', 'group:projectC:1.0'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar', 'projectB-1.0.jar', 'projectC-1.0.jar']
}
"""

        when:
        server.expectGet('/repo1/group/projectA/1.0/ivy-1.0.xml', moduleA.ivyFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', moduleA.jarFile)

        // Handles missing in repo1
        server.expectGetMissing('/repo1/group/projectB/1.0/ivy-1.0.xml')
        server.expectHeadMissing('/repo1/group/projectB/1.0/projectB-1.0.jar')

        server.expectGet('/repo2/group/projectB/1.0/ivy-1.0.xml', moduleB.ivyFile)
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', moduleB.jarFile)

        // Handles from broken url in repo1 (but does not cache)
        server.addBroken('/repo1/group/projectC')
        server.expectGet('/repo2/group/projectC/1.0/ivy-1.0.xml', moduleC.ivyFile)
        server.expectGet('/repo2/group/projectC/1.0/projectC-1.0.jar', moduleC.jarFile)
        and:
        progressLogger.withProgressLogging(executer, moduleA.jarFile, moduleB.jarFile, moduleC.jarFile)
        then:
        succeeds('listJars')
        and:
        progressLogger.downloadProgressLogged("http://localhost:${server.port}/repo1/group/projectA/1.0/ivy-1.0.xml")
        progressLogger.downloadProgressLogged("http://localhost:${server.port}/repo1/group/projectA/1.0/projectA-1.0.jar")
        progressLogger.downloadProgressLogged("http://localhost:${server.port}/repo2/group/projectB/1.0/ivy-1.0.xml")
        progressLogger.downloadProgressLogged("http://localhost:${server.port}/repo2/group/projectB/1.0/projectB-1.0.jar")
        progressLogger.downloadProgressLogged("http://localhost:${server.port}/repo2/group/projectC/1.0/ivy-1.0.xml")
        progressLogger.downloadProgressLogged("http://localhost:${server.port}/repo2/group/projectC/1.0/projectC-1.0.jar")

        when:
        server.resetExpectations()
        progressLogger.resetExpectations()
        progressLogger.noProgressLogged()
        server.addBroken('/repo1/group/projectC') // Will always re-attempt a broken repository
        // No extra calls for cached dependencies

        then:
        succeeds('listJars')
    }

    public void "uses all configured patterns to resolve artifacts and caches result"() {
        server.start()

        given:
        def module = ivyRepo().module('group', 'projectA', '1.2').publish()

        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}/first"
        artifactPattern "http://localhost:${server.port}/second/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
        artifactPattern "http://localhost:${server.port}/third/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]"
        ivyPattern "http://localhost:${server.port}/second/[module]/[revision]/ivy.xml"
        ivyPattern "http://localhost:${server.port}/third/[module]/[revision]/ivy.xml"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task show << { println configurations.compile.files }
"""

        when:
        server.expectGetMissing('/first/group/projectA/1.2/ivy-1.2.xml')
        server.expectGetMissing('/first/group/projectA/1.2/projectA-1.2.jar')
        server.expectGetMissing('/second/projectA/1.2/ivy.xml')
        server.expectGetMissing('/second/projectA/1.2/projectA-1.2.jar')
        server.expectGet('/third/projectA/1.2/ivy.xml', module.ivyFile)
        server.expectGet('/third/projectA/1.2/projectA-1.2.jar', module.jarFile)

        then:
        succeeds('show')

        when:
        server.resetExpectations()

        then:
        succeeds('show')
    }

    public void "replaces org.name with org/name when using maven layout"() {
        server.start()

        given:
        def module = ivyRepo().module('org.name.here', 'projectA', '1.2').publish()

        buildFile << """
repositories {
    ivy {
        url "http://localhost:${server.port}"
        layout "maven"
    }
}
configurations { compile }
dependencies {
    compile 'org.name.here:projectA:1.2'
}

task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        when:
        server.expectGet('/org/name/here/projectA/1.2/ivy-1.2.xml', module.ivyFile)
        server.expectGet('/org/name/here/projectA/1.2/projectA-1.2.jar', module.jarFile)

        and:
        succeeds('retrieve')

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }
}
